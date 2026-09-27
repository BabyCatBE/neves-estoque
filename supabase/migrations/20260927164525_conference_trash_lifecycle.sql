begin;

create or replace function private.refresh_product_merge_reconfirmation(
  p_product_id uuid
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_latest_merge_at timestamptz;
  v_required_at timestamptz;
begin
  if p_product_id is null then
    return;
  end if;

  select max(a.created_at)
    into v_latest_merge_at
  from public.audit_log a
  where a.action = 'PRODUCT_MERGE'
    and a.entity_type = 'products'
    and a.entity_id = p_product_id;

  if v_latest_merge_at is null then
    return;
  end if;

  if exists (
    select 1
    from public.conference_items ci
    join public.conferences c on c.id = ci.conference_id
    where ci.product_id = p_product_id
      and c.deleted_at is null
      and c.created_at >= v_latest_merge_at
  ) then
    v_required_at := null;
  else
    v_required_at := v_latest_merge_at;
  end if;

  perform set_config('app.suppress_row_audit', 'on', true);

  update public.products p
  set stock_reconfirmation_required_at = v_required_at
  where p.id = p_product_id
    and p.deleted_at is null
    and p.stock_reconfirmation_required_at is distinct from v_required_at;

  perform set_config('app.suppress_row_audit', 'off', true);
exception
  when others then
    perform set_config('app.suppress_row_audit', 'off', true);
    raise;
end;
$$;

revoke all on function private.refresh_product_merge_reconfirmation(uuid)
from public, anon, authenticated, service_role;

create or replace function private.soft_delete_conference_impl(
  p_conference_id uuid,
  p_device_id uuid
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_product_id uuid;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  perform 1
  from public.conferences c
  where c.id = p_conference_id
    and c.deleted_at is null
  for update;

  if not found then
    raise exception 'Conferência não encontrada ou já excluída.';
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  update public.conferences
  set deleted_at = now()
  where id = p_conference_id
    and deleted_at is null;

  for v_product_id in
    select distinct ci.product_id
    from public.conference_items ci
    where ci.conference_id = p_conference_id
  loop
    perform private.refresh_product_merge_reconfirmation(v_product_id);
  end loop;
end;
$$;

revoke all on function private.soft_delete_conference_impl(uuid, uuid)
from public, anon, authenticated, service_role;

grant execute on function private.soft_delete_conference_impl(uuid, uuid)
to authenticated, service_role;

create or replace function public.soft_delete_conference(
  p_conference_id uuid,
  p_device_id uuid
)
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.soft_delete_conference_impl(p_conference_id, p_device_id);
$$;

revoke all on function public.soft_delete_conference(uuid, uuid)
from public, anon, authenticated, service_role;

grant execute on function public.soft_delete_conference(uuid, uuid)
to authenticated, service_role;

create or replace function private.restore_conference_impl(
  p_conference_id uuid,
  p_device_id uuid
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_restore_until timestamptz;
  v_product_id uuid;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  select c.restore_until
    into v_restore_until
  from public.conferences c
  where c.id = p_conference_id
    and c.deleted_at is not null
  for update;

  if not found then
    raise exception 'Conferência não encontrada na lixeira.';
  end if;

  if v_restore_until is null or now() > v_restore_until then
    raise exception 'Prazo de restauração expirado.';
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  update public.conferences
  set deleted_at = null
  where id = p_conference_id
    and deleted_at is not null;

  for v_product_id in
    select distinct ci.product_id
    from public.conference_items ci
    where ci.conference_id = p_conference_id
  loop
    perform private.refresh_product_merge_reconfirmation(v_product_id);
  end loop;
end;
$$;

revoke all on function private.restore_conference_impl(uuid, uuid)
from public, anon, authenticated, service_role;

grant execute on function private.restore_conference_impl(uuid, uuid)
to authenticated, service_role;

create or replace function public.restore_conference(
  p_conference_id uuid,
  p_device_id uuid
)
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.restore_conference_impl(p_conference_id, p_device_id);
$$;

revoke all on function public.restore_conference(uuid, uuid)
from public, anon, authenticated, service_role;

grant execute on function public.restore_conference(uuid, uuid)
to authenticated, service_role;

commit;
