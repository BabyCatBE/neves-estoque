begin;

create or replace function private.create_product_conference_impl(
  p_product_id uuid,
  p_effective_at timestamptz,
  p_physical_responsible text,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_quantity numeric,
  p_observation text default null
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_conference_id uuid;
  v_existing_user uuid;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;

  if p_product_id is null
    or not exists (
      select 1 from public.products p
      where p.id = p_product_id and p.deleted_at is null
    )
  then
    raise exception 'Produto inválido ou excluído.';
  end if;

  if p_effective_at is null then raise exception 'Data/hora efetiva da Conferência é obrigatória.'; end if;

  if char_length(btrim(coalesce(p_physical_responsible, ''))) < 1
    or char_length(btrim(coalesce(p_physical_responsible, ''))) > 160
  then
    raise exception 'Responsável físico inválido.';
  end if;

  if p_idempotency_key is null then raise exception 'Chave de idempotência é obrigatória.'; end if;
  if p_quantity is null or p_quantity < 0 then raise exception 'Quantidade não pode ser negativa.'; end if;

  select c.id, c.registered_by into v_conference_id, v_existing_user
  from public.conferences c
  where c.idempotency_key = p_idempotency_key
  limit 1;

  if v_conference_id is not null then
    if v_existing_user <> v_uid then raise exception 'Chave de idempotência já utilizada.'; end if;
    return v_conference_id;
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  insert into public.conferences (
    effective_at, physical_responsible, observation, registered_by, device_id,
    idempotency_key, scope_type, category_id, scope_product_id
  )
  values (
    p_effective_at, btrim(p_physical_responsible),
    nullif(btrim(coalesce(p_observation, '')), ''), v_uid, p_device_id,
    p_idempotency_key, 'product', null, p_product_id
  )
  returning id into v_conference_id;

  insert into public.conference_items (conference_id, product_id, quantity, position)
  values (v_conference_id, p_product_id, p_quantity, 1);

  return v_conference_id;
end;
$$;

revoke all on function private.create_product_conference_impl(
  uuid, timestamptz, text, uuid, uuid, numeric, text
) from public, anon, authenticated, service_role;
grant execute on function private.create_product_conference_impl(
  uuid, timestamptz, text, uuid, uuid, numeric, text
) to authenticated, service_role;

create or replace function public.create_product_conference(
  p_product_id uuid,
  p_effective_at timestamptz,
  p_physical_responsible text,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_quantity numeric,
  p_observation text default null
)
returns uuid
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.create_product_conference_impl(
    p_product_id, p_effective_at, p_physical_responsible, p_device_id,
    p_idempotency_key, p_quantity, p_observation
  );
$$;

revoke all on function public.create_product_conference(
  uuid, timestamptz, text, uuid, uuid, numeric, text
) from public, anon, authenticated, service_role;
grant execute on function public.create_product_conference(
  uuid, timestamptz, text, uuid, uuid, numeric, text
) to authenticated, service_role;

commit;
