begin;

create or replace function private.audit_row_change()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_before jsonb;
  v_after jsonb;
  v_id uuid;
  v_action text;
  v_device uuid;
  v_candidate text;
begin
  if coalesce(current_setting('app.suppress_row_audit', true), '') = 'on' then
    if tg_op = 'DELETE' then
      return old;
    end if;
    return new;
  end if;

  if tg_op = 'INSERT' then
    v_before := null;
    v_after := to_jsonb(new);
    v_action := 'CREATE';
  elsif tg_op = 'UPDATE' then
    v_before := to_jsonb(old);
    v_after := to_jsonb(new);

    -- Atualizações técnicas recorrentes não representam uma ação funcional
    -- do usuário e não devem poluir a trilha de auditoria.
    if tg_table_name = 'app_users'
       and (v_before - 'updated_at' - 'updated_by')
           = (v_after - 'updated_at' - 'updated_by') then
      return new;
    end if;

    if tg_table_name = 'devices'
       and (v_before - 'last_seen_at' - 'updated_at' - 'updated_by')
           = (v_after - 'last_seen_at' - 'updated_at' - 'updated_by') then
      return new;
    end if;

    if (v_before ->> 'deleted_at') is null and (v_after ->> 'deleted_at') is not null then
      v_action := 'SOFT_DELETE';
    elsif (v_before ->> 'deleted_at') is not null and (v_after ->> 'deleted_at') is null then
      v_action := 'RESTORE';
    else
      v_action := 'UPDATE';
    end if;
  else
    v_before := to_jsonb(old);
    v_after := null;
    v_action := 'DELETE';
  end if;

  begin
    v_id := coalesce(v_after ->> 'id', v_before ->> 'id')::uuid;
  exception
    when others then
      v_id := null;
  end;

  v_candidate := coalesce(v_after ->> 'device_id', v_before ->> 'device_id');

  begin
    if v_candidate is not null and btrim(v_candidate) <> '' then
      v_device := v_candidate::uuid;
      if not private.is_device_allowed(v_device) then
        v_device := null;
      end if;
    else
      v_device := private.request_device_id();
    end if;
  exception
    when others then
      v_device := private.request_device_id();
  end;

  insert into public.audit_log (
    actor_user_id,
    device_id,
    action,
    entity_type,
    entity_id,
    before_data,
    after_data
  )
  values (
    auth.uid(),
    v_device,
    v_action,
    tg_table_name,
    v_id,
    v_before,
    v_after
  );

  if tg_op = 'DELETE' then
    return old;
  end if;

  return new;
end;
$$;

revoke all on function private.audit_row_change()
from public, anon, authenticated;

create or replace function private.claim_app_access_impl()
returns table(app_user_id uuid, role_name text)
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_email text := lower(btrim(coalesce(auth.jwt() ->> 'email', '')));
  v_provider text := coalesce(auth.jwt() -> 'app_metadata' ->> 'provider', '');
  v_current_auth_user_id uuid;
begin
  if v_uid is null or v_provider <> 'google' or v_email = '' then
    raise exception 'Acesso não autorizado.';
  end if;

  select au.id, au.role_name, au.auth_user_id
    into app_user_id, role_name, v_current_auth_user_id
  from public.app_users au
  where au.is_authorized
    and lower(btrim(au.email)) = v_email
    and (au.auth_user_id is null or au.auth_user_id = v_uid)
  for update;

  if app_user_id is null then
    raise exception 'Acesso não autorizado.';
  end if;

  -- O vínculo com auth.users só precisa ser escrito uma vez.
  -- Sessões seguintes apenas validam o acesso, sem UPDATE redundante.
  if v_current_auth_user_id is null then
    update public.app_users au
    set auth_user_id = v_uid
    where au.id = app_user_id;
  end if;

  return next;
end;
$$;

commit;
