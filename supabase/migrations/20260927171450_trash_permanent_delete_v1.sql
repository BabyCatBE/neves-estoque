-- Applied in Supabase as 20260927171450_trash_permanent_delete_v1.
-- Permanent deletion here means irreversible in the app while preserving
-- the historical row required by older Entries, Conferences, prices and reports.
begin;

alter table public.products
  add column if not exists permanently_deleted_at timestamptz,
  add column if not exists permanently_deleted_by uuid references auth.users(id) on delete set null;
alter table public.categories
  add column if not exists permanently_deleted_at timestamptz,
  add column if not exists permanently_deleted_by uuid references auth.users(id) on delete set null;
alter table public.suppliers
  add column if not exists permanently_deleted_at timestamptz,
  add column if not exists permanently_deleted_by uuid references auth.users(id) on delete set null;
alter table public.entries
  add column if not exists permanently_deleted_at timestamptz,
  add column if not exists permanently_deleted_by uuid references auth.users(id) on delete set null;
alter table public.conferences
  add column if not exists permanently_deleted_at timestamptz,
  add column if not exists permanently_deleted_by uuid references auth.users(id) on delete set null;

create or replace function private.guard_permanent_delete_state()
returns trigger
language plpgsql
security definer
set search_path=pg_catalog
as $$
begin
  if old.permanently_deleted_at is null and new.permanently_deleted_at is not null then
    if current_setting('app.allow_permanent_delete',true) is distinct from 'on' then
      raise exception 'Exclusão definitiva só pode ser feita pelo fluxo protegido da Lixeira.';
    end if;
  end if;

  if old.permanently_deleted_at is not null then
    new.permanently_deleted_at := old.permanently_deleted_at;
    new.permanently_deleted_by := old.permanently_deleted_by;
    if old.deleted_at is not null and new.deleted_at is null then
      raise exception 'Item excluído definitivamente e não pode ser restaurado.';
    end if;
  end if;

  return new;
end;
$$;

revoke all on function private.guard_permanent_delete_state()
from public,anon,authenticated,service_role;

do $$
declare v_table text;
begin
  foreach v_table in array array['products','categories','suppliers','entries','conferences']
  loop
    execute format('drop trigger if exists %I_guard_permanent_delete on public.%I',v_table,v_table);
    execute format(
      'create trigger %I_guard_permanent_delete before update on public.%I
       for each row execute function private.guard_permanent_delete_state()',
      v_table,v_table
    );
  end loop;
end;
$$;

create or replace function private.permanently_delete_trash_item_internal(
  p_item_type text,p_item_id uuid,p_actor_user_id uuid,p_device_id uuid
)
returns void
language plpgsql
security definer
set search_path=pg_catalog,public,private
as $$
declare
  v_item_type text := lower(btrim(coalesce(p_item_type,'')));
  v_entity_type text;
  v_before jsonb;
  v_after jsonb;
begin
  if p_item_id is null then raise exception 'Item inválido.'; end if;

  case v_item_type
    when 'product' then
      select to_jsonb(t) into v_before from public.products t
      where t.id=p_item_id and t.deleted_at is not null and t.restore_until>now()
        and t.permanently_deleted_at is null for update;
      v_entity_type := 'products';
    when 'category' then
      select to_jsonb(t) into v_before from public.categories t
      where t.id=p_item_id and t.deleted_at is not null and t.restore_until>now()
        and t.permanently_deleted_at is null for update;
      v_entity_type := 'categories';
    when 'supplier' then
      select to_jsonb(t) into v_before from public.suppliers t
      where t.id=p_item_id and t.deleted_at is not null and t.restore_until>now()
        and t.permanently_deleted_at is null for update;
      v_entity_type := 'suppliers';
    when 'entry' then
      select to_jsonb(t) into v_before from public.entries t
      where t.id=p_item_id and t.deleted_at is not null and t.restore_until>now()
        and t.permanently_deleted_at is null for update;
      v_entity_type := 'entries';
    when 'conference' then
      select to_jsonb(t) into v_before from public.conferences t
      where t.id=p_item_id and t.deleted_at is not null and t.restore_until>now()
        and t.permanently_deleted_at is null for update;
      v_entity_type := 'conferences';
    else
      raise exception 'Tipo de item inválido.';
  end case;

  if v_before is null then
    raise exception 'Item não está disponível para exclusão definitiva.';
  end if;

  perform set_config('app.device_id',p_device_id::text,true);
  perform set_config('app.allow_permanent_delete','on',true);
  perform set_config('app.suppress_row_audit','on',true);

  begin
    case v_item_type
      when 'product' then
        update public.products set permanently_deleted_at=now(),permanently_deleted_by=p_actor_user_id
        where id=p_item_id returning to_jsonb(products.*) into v_after;
      when 'category' then
        update public.categories set permanently_deleted_at=now(),permanently_deleted_by=p_actor_user_id
        where id=p_item_id returning to_jsonb(categories.*) into v_after;
      when 'supplier' then
        update public.suppliers set permanently_deleted_at=now(),permanently_deleted_by=p_actor_user_id
        where id=p_item_id returning to_jsonb(suppliers.*) into v_after;
      when 'entry' then
        update public.entries set permanently_deleted_at=now(),permanently_deleted_by=p_actor_user_id
        where id=p_item_id returning to_jsonb(entries.*) into v_after;
      when 'conference' then
        update public.conferences set permanently_deleted_at=now(),permanently_deleted_by=p_actor_user_id
        where id=p_item_id returning to_jsonb(conferences.*) into v_after;
    end case;
    perform set_config('app.suppress_row_audit','off',true);
    perform set_config('app.allow_permanent_delete','off',true);
  exception when others then
    perform set_config('app.suppress_row_audit','off',true);
    perform set_config('app.allow_permanent_delete','off',true);
    raise;
  end;

  insert into public.audit_log(
    actor_user_id,device_id,action,entity_type,entity_id,before_data,after_data,metadata
  ) values(
    p_actor_user_id,p_device_id,'PERMANENT_DELETE',v_entity_type,p_item_id,v_before,v_after,
    jsonb_build_object('trash_item_type',v_item_type,'historical_identity_preserved',true)
  );
end;
$$;

revoke all on function private.permanently_delete_trash_item_internal(text,uuid,uuid,uuid)
from public,anon,authenticated,service_role;

create or replace function private.permanently_delete_trash_item_impl(
  p_item_type text,p_item_id uuid,p_device_id uuid
)
returns void
language plpgsql
security definer
set search_path=pg_catalog,public,private,auth
as $$
declare v_uid uuid := auth.uid();
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;
  perform private.permanently_delete_trash_item_internal(p_item_type,p_item_id,v_uid,p_device_id);
end;
$$;

revoke all on function private.permanently_delete_trash_item_impl(text,uuid,uuid)
from public,anon,authenticated,service_role;
grant execute on function private.permanently_delete_trash_item_impl(text,uuid,uuid)
to authenticated,service_role;

create or replace function public.permanently_delete_trash_item(
  p_item_type text,p_item_id uuid,p_device_id uuid
)
returns void
language sql
security invoker
set search_path=pg_catalog,private
as $$
  select private.permanently_delete_trash_item_impl(p_item_type,p_item_id,p_device_id);
$$;

revoke all on function public.permanently_delete_trash_item(text,uuid,uuid)
from public,anon,authenticated,service_role;
grant execute on function public.permanently_delete_trash_item(text,uuid,uuid)
to authenticated,service_role;

create or replace function private.empty_trash_impl(p_device_id uuid)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog,public,private,auth
as $$
declare
  v_uid uuid := auth.uid();
  v_id uuid;
  v_products integer := 0;
  v_categories integer := 0;
  v_suppliers integer := 0;
  v_entries integer := 0;
  v_conferences integer := 0;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;

  for v_id in select id from public.products
    where deleted_at is not null and restore_until>now() and permanently_deleted_at is null
    order by deleted_at,id
  loop
    perform private.permanently_delete_trash_item_internal('product',v_id,v_uid,p_device_id);
    v_products := v_products+1;
  end loop;

  for v_id in select id from public.categories
    where deleted_at is not null and restore_until>now() and permanently_deleted_at is null
    order by deleted_at,id
  loop
    perform private.permanently_delete_trash_item_internal('category',v_id,v_uid,p_device_id);
    v_categories := v_categories+1;
  end loop;

  for v_id in select id from public.suppliers
    where deleted_at is not null and restore_until>now() and permanently_deleted_at is null
    order by deleted_at,id
  loop
    perform private.permanently_delete_trash_item_internal('supplier',v_id,v_uid,p_device_id);
    v_suppliers := v_suppliers+1;
  end loop;

  for v_id in select id from public.entries
    where deleted_at is not null and restore_until>now() and permanently_deleted_at is null
    order by deleted_at,id
  loop
    perform private.permanently_delete_trash_item_internal('entry',v_id,v_uid,p_device_id);
    v_entries := v_entries+1;
  end loop;

  for v_id in select id from public.conferences
    where deleted_at is not null and restore_until>now() and permanently_deleted_at is null
    order by deleted_at,id
  loop
    perform private.permanently_delete_trash_item_internal('conference',v_id,v_uid,p_device_id);
    v_conferences := v_conferences+1;
  end loop;

  insert into public.audit_log(actor_user_id,device_id,action,entity_type,metadata)
  values(
    v_uid,p_device_id,'EMPTY_TRASH','trash',
    jsonb_build_object(
      'products',v_products,'categories',v_categories,'suppliers',v_suppliers,
      'entries',v_entries,'conferences',v_conferences,
      'total',v_products+v_categories+v_suppliers+v_entries+v_conferences,
      'historical_identity_preserved',true
    )
  );

  return jsonb_build_object(
    'products',v_products,'categories',v_categories,'suppliers',v_suppliers,
    'entries',v_entries,'conferences',v_conferences,
    'total',v_products+v_categories+v_suppliers+v_entries+v_conferences
  );
end;
$$;

revoke all on function private.empty_trash_impl(uuid)
from public,anon,authenticated,service_role;
grant execute on function private.empty_trash_impl(uuid)
to authenticated,service_role;

create or replace function public.empty_trash(p_device_id uuid)
returns jsonb
language sql
security invoker
set search_path=pg_catalog,private
as $$
  select private.empty_trash_impl(p_device_id);
$$;

revoke all on function public.empty_trash(uuid)
from public,anon,authenticated,service_role;
grant execute on function public.empty_trash(uuid)
to authenticated,service_role;

commit;
