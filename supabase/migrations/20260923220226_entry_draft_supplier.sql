create or replace function private.create_entry_with_draft_supplier_impl(
  p_supplier_name text,
  p_effective_at timestamptz,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_items jsonb,
  p_observation text default null
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_existing_entry uuid;
  v_existing_user uuid;
  v_supplier_id uuid;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  if p_idempotency_key is null then
    raise exception 'Chave de idempotência é obrigatória.';
  end if;

  select e.id, e.registered_by
    into v_existing_entry, v_existing_user
  from public.entries e
  where e.idempotency_key = p_idempotency_key
  limit 1;

  if v_existing_entry is not null then
    if v_existing_user <> v_uid then
      raise exception 'Chave de idempotência já utilizada.';
    end if;
    return v_existing_entry;
  end if;

  v_supplier_id := private.create_supplier_impl(p_name => p_supplier_name);

  return private.create_entry_impl(
    v_supplier_id,
    p_effective_at,
    p_device_id,
    p_idempotency_key,
    p_items,
    p_observation
  );
end;
$$;

create or replace function public.create_entry_with_draft_supplier(
  p_supplier_name text,
  p_effective_at timestamptz,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_items jsonb,
  p_observation text default null
)
returns uuid
language sql
set search_path = pg_catalog, private
as $$
  select private.create_entry_with_draft_supplier_impl(
    p_supplier_name,
    p_effective_at,
    p_device_id,
    p_idempotency_key,
    p_items,
    p_observation
  );
$$;

revoke execute on function public.create_entry_with_draft_supplier(text,timestamptz,uuid,uuid,jsonb,text) from public, anon;
grant execute on function public.create_entry_with_draft_supplier(text,timestamptz,uuid,uuid,jsonb,text) to authenticated, service_role;

revoke execute on function private.create_entry_with_draft_supplier_impl(text,timestamptz,uuid,uuid,jsonb,text) from public, anon;
grant execute on function private.create_entry_with_draft_supplier_impl(text,timestamptz,uuid,uuid,jsonb,text) to authenticated, service_role;
