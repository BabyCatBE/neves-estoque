-- RPCs operacionais iniciais + ajuste de policies/telemetria de dispositivo.

begin;

-- ---------------------------------------------------------------------------
-- Ajuste do contexto de dispositivo para auditoria
-- ---------------------------------------------------------------------------

create or replace function private.request_device_id()
returns uuid
language plpgsql
stable
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_headers jsonb;
  v_raw text;
  v_device uuid;
begin
  begin
    v_raw := current_setting('app.device_id', true);

    if v_raw is null or btrim(v_raw) = '' then
      v_headers := current_setting('request.headers', true)::jsonb;
      v_raw := v_headers ->> 'x-device-id';
    end if;

    if v_raw is null or btrim(v_raw) = '' then
      return null;
    end if;

    v_device := v_raw::uuid;
  exception
    when others then
      return null;
  end;

  if private.is_device_allowed(v_device) then
    return v_device;
  end if;

  return null;
end;
$$;

revoke all on function private.request_device_id() from public, anon;
grant execute on function private.request_device_id() to authenticated;

-- ---------------------------------------------------------------------------
-- Ajuste de policies para initPlan de auth/helper
-- ---------------------------------------------------------------------------

drop policy if exists app_users_select_self on public.app_users;

create policy app_users_select_self
on public.app_users
for select
to authenticated
using (
  (select auth.uid()) is not null
  and (select private.is_google_user())
  and (
    auth_user_id = (select auth.uid())
    or (
      auth_user_id is null
      and lower(btrim(email)) = lower(
        btrim(coalesce((select auth.jwt() ->> 'email'), ''))
      )
    )
  )
);

drop policy if exists devices_select_own on public.devices;

create policy devices_select_own
on public.devices
for select
to authenticated
using (
  (select private.is_authorized_user())
  and user_id = (select auth.uid())
);

-- Índices úteis para FKs consultadas com frequência.
create index if not exists entries_device_id_idx
  on public.entries (device_id);

create index if not exists conferences_device_id_idx
  on public.conferences (device_id);

create index if not exists audit_log_device_id_idx
  on public.audit_log (device_id);

-- ---------------------------------------------------------------------------
-- Criação segura de Entrada
-- ---------------------------------------------------------------------------

create or replace function public.create_entry(
  p_supplier_id uuid,
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
  v_entry_id uuid;
  v_existing_user uuid;
  v_item jsonb;
  v_position integer := 0;
  v_product_id uuid;
  v_quantity numeric;
  v_unit_price numeric;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  if p_supplier_id is null
    or not exists (
      select 1
      from public.suppliers s
      where s.id = p_supplier_id
        and s.deleted_at is null
    )
  then
    raise exception 'Fornecedor inválido ou excluído.';
  end if;

  if p_effective_at is null then
    raise exception 'Data/hora efetiva da Entrada é obrigatória.';
  end if;

  if p_idempotency_key is null then
    raise exception 'Chave de idempotência é obrigatória.';
  end if;

  if p_items is null
    or jsonb_typeof(p_items) <> 'array'
    or jsonb_array_length(p_items) = 0
  then
    raise exception 'A Entrada precisa de pelo menos um item.';
  end if;

  select e.id, e.registered_by
  into v_entry_id, v_existing_user
  from public.entries e
  where e.idempotency_key = p_idempotency_key
  limit 1;

  if v_entry_id is not null then
    if v_existing_user <> v_uid then
      raise exception 'Chave de idempotência já utilizada.';
    end if;

    return v_entry_id;
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  insert into public.entries (
    supplier_id,
    effective_at,
    observation,
    registered_by,
    device_id,
    idempotency_key
  )
  values (
    p_supplier_id,
    p_effective_at,
    nullif(btrim(coalesce(p_observation, '')), ''),
    v_uid,
    p_device_id,
    p_idempotency_key
  )
  returning id into v_entry_id;

  for v_item in
    select value
    from jsonb_array_elements(p_items)
  loop
    v_position := v_position + 1;

    begin
      v_product_id := nullif(v_item ->> 'product_id', '')::uuid;
      v_quantity := nullif(v_item ->> 'quantity', '')::numeric;

      if v_item ? 'unit_price'
        and v_item ->> 'unit_price' is not null
        and btrim(v_item ->> 'unit_price') <> ''
      then
        v_unit_price := (v_item ->> 'unit_price')::numeric;
      else
        v_unit_price := null;
      end if;
    exception
      when others then
        raise exception 'Item % possui formato inválido.', v_position;
    end;

    if v_product_id is null
      or not exists (
        select 1
        from public.products p
        where p.id = v_product_id
          and p.deleted_at is null
      )
    then
      raise exception 'Produto inválido ou excluído no item %.', v_position;
    end if;

    if v_quantity is null or v_quantity <= 0 then
      raise exception 'Quantidade deve ser maior que zero no item %.', v_position;
    end if;

    if v_unit_price is not null and v_unit_price < 0 then
      raise exception 'Preço não pode ser negativo no item %.', v_position;
    end if;

    insert into public.entry_items (
      entry_id,
      product_id,
      quantity,
      unit_price,
      position
    )
    values (
      v_entry_id,
      v_product_id,
      v_quantity,
      v_unit_price,
      v_position
    );
  end loop;

  return v_entry_id;
end;
$$;

revoke all on function public.create_entry(
  uuid, timestamptz, uuid, uuid, jsonb, text
) from public, anon, authenticated;

grant execute on function public.create_entry(
  uuid, timestamptz, uuid, uuid, jsonb, text
) to authenticated;

-- ---------------------------------------------------------------------------
-- Criação segura de Conferência
-- ---------------------------------------------------------------------------

create or replace function public.create_conference(
  p_effective_at timestamptz,
  p_physical_responsible text,
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
  v_conference_id uuid;
  v_existing_user uuid;
  v_item jsonb;
  v_position integer := 0;
  v_product_id uuid;
  v_quantity numeric;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  if p_effective_at is null then
    raise exception 'Data/hora efetiva da Conferência é obrigatória.';
  end if;

  if char_length(btrim(coalesce(p_physical_responsible, ''))) < 1
    or char_length(btrim(coalesce(p_physical_responsible, ''))) > 160
  then
    raise exception 'Responsável físico inválido.';
  end if;

  if p_idempotency_key is null then
    raise exception 'Chave de idempotência é obrigatória.';
  end if;

  if p_items is null
    or jsonb_typeof(p_items) <> 'array'
    or jsonb_array_length(p_items) = 0
  then
    raise exception 'A Conferência precisa de pelo menos um item.';
  end if;

  select c.id, c.registered_by
  into v_conference_id, v_existing_user
  from public.conferences c
  where c.idempotency_key = p_idempotency_key
  limit 1;

  if v_conference_id is not null then
    if v_existing_user <> v_uid then
      raise exception 'Chave de idempotência já utilizada.';
    end if;

    return v_conference_id;
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  insert into public.conferences (
    effective_at,
    physical_responsible,
    observation,
    registered_by,
    device_id,
    idempotency_key
  )
  values (
    p_effective_at,
    btrim(p_physical_responsible),
    nullif(btrim(coalesce(p_observation, '')), ''),
    v_uid,
    p_device_id,
    p_idempotency_key
  )
  returning id into v_conference_id;

  for v_item in
    select value
    from jsonb_array_elements(p_items)
  loop
    v_position := v_position + 1;

    begin
      v_product_id := nullif(v_item ->> 'product_id', '')::uuid;
      v_quantity := nullif(v_item ->> 'quantity', '')::numeric;
    exception
      when others then
        raise exception 'Item % possui formato inválido.', v_position;
    end;

    if v_product_id is null
      or not exists (
        select 1
        from public.products p
        where p.id = v_product_id
          and p.deleted_at is null
      )
    then
      raise exception 'Produto inválido ou excluído no item %.', v_position;
    end if;

    if v_quantity is null or v_quantity < 0 then
      raise exception 'Quantidade não pode ser negativa no item %.', v_position;
    end if;

    if exists (
      select 1
      from public.conference_items ci
      where ci.conference_id = v_conference_id
        and ci.product_id = v_product_id
    ) then
      raise exception 'Produto repetido na Conferência no item %.', v_position;
    end if;

    insert into public.conference_items (
      conference_id,
      product_id,
      quantity,
      position
    )
    values (
      v_conference_id,
      v_product_id,
      v_quantity,
      v_position
    );
  end loop;

  return v_conference_id;
end;
$$;

revoke all on function public.create_conference(
  timestamptz, text, uuid, uuid, jsonb, text
) from public, anon, authenticated;

grant execute on function public.create_conference(
  timestamptz, text, uuid, uuid, jsonb, text
) to authenticated;

commit;
