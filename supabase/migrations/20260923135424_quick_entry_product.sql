alter table public.products
  add column quick_entry_idempotency_key uuid;

alter table public.products
  add constraint products_pending_requires_entry_key_check
  check (category_id is not null or quick_entry_idempotency_key is not null);

create index products_pending_entry_key_idx
  on public.products (quick_entry_idempotency_key)
  where category_id is null and deleted_at is null;

create or replace function private.create_quick_entry_product_impl(
  p_name text,
  p_unit text,
  p_entry_idempotency_key uuid,
  p_category_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_product_id uuid;
  v_name text;
  v_unit text;
  v_next_order integer;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_entry_idempotency_key is null then
    raise exception 'Chave da Entrada é obrigatória.';
  end if;

  v_name := regexp_replace(btrim(coalesce(p_name, '')), '[[:space:]]+', ' ', 'g');
  if char_length(v_name) < 1 or char_length(v_name) > 200 then
    raise exception 'Nome do produto inválido.';
  end if;

  v_unit := upper(btrim(coalesce(p_unit, '')));
  if v_unit not in ('UN','KG','SC','CX','PCT','FD','BL','GL','PET','ROLO','LATA','BARRA','PT') then
    raise exception 'Unidade inválida.';
  end if;

  if p_category_id is not null then
    perform 1
    from public.categories c
    where c.id = p_category_id
      and c.deleted_at is null
    for update;

    if not found then
      raise exception 'Categoria inválida ou excluída.';
    end if;

    select coalesce(max(p.sort_order), 0) + 1
      into v_next_order
    from public.products p
    where p.category_id = p_category_id
      and p.deleted_at is null;
  else
    v_next_order := null;
  end if;

  insert into public.products (
    name,
    category_id,
    unit,
    sort_order,
    quick_entry_idempotency_key
  )
  values (
    v_name,
    p_category_id,
    v_unit,
    v_next_order,
    p_entry_idempotency_key
  )
  returning id into v_product_id;

  return v_product_id;
end;
$$;

create or replace function public.create_quick_entry_product(
  p_name text,
  p_unit text,
  p_entry_idempotency_key uuid,
  p_category_id uuid default null
)
returns uuid
language sql
set search_path = pg_catalog, private
as $$
  select private.create_quick_entry_product_impl(
    p_name,
    p_unit,
    p_entry_idempotency_key,
    p_category_id
  );
$$;

revoke execute on function public.create_quick_entry_product(text, text, uuid, uuid) from public, anon;
grant execute on function public.create_quick_entry_product(text, text, uuid, uuid) to authenticated, service_role;

revoke execute on function private.create_quick_entry_product_impl(text, text, uuid, uuid) from public, anon;
grant execute on function private.create_quick_entry_product_impl(text, text, uuid, uuid) to authenticated, service_role;

create or replace function private.create_entry_impl(
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
          and (
            p.category_id is not null
            or p.quick_entry_idempotency_key = p_idempotency_key
          )
      )
    then
      raise exception 'Produto inválido, excluído ou pendente de categoria no item %.', v_position;
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

create or replace function private.create_conference_impl(
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
          and p.category_id is not null
      )
    then
      raise exception 'Produto inválido, excluído ou com cadastro pendente no item %.', v_position;
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

create or replace function private.update_entry_impl(
  p_entry_id uuid,
  p_supplier_id uuid,
  p_effective_at timestamptz,
  p_device_id uuid,
  p_items jsonb,
  p_observation text default null
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_item jsonb;
  v_position integer := 0;
  v_product_id uuid;
  v_quantity numeric;
  v_unit_price numeric;
  v_before jsonb;
  v_after jsonb;
  v_entry_idempotency_key uuid;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  if p_entry_id is null then
    raise exception 'Entrada inválida.';
  end if;

  select e.idempotency_key
    into v_entry_idempotency_key
  from public.entries e
  where e.id = p_entry_id
    and e.deleted_at is null
  for update;

  if not found then
    raise exception 'Entrada não encontrada.';
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

  if p_items is null
    or jsonb_typeof(p_items) <> 'array'
    or jsonb_array_length(p_items) = 0
  then
    raise exception 'A Entrada precisa de pelo menos um item.';
  end if;

  select jsonb_build_object(
    'entry', to_jsonb(e),
    'items', coalesce((
      select jsonb_agg(to_jsonb(ei) order by ei.position)
      from public.entry_items ei
      where ei.entry_id = e.id
    ), '[]'::jsonb)
  )
  into v_before
  from public.entries e
  where e.id = p_entry_id;

  perform set_config('app.device_id', p_device_id::text, true);
  perform set_config('app.suppress_row_audit', 'on', true);

  update public.entries
  set
    supplier_id = p_supplier_id,
    effective_at = p_effective_at,
    observation = nullif(btrim(coalesce(p_observation, '')), '')
  where id = p_entry_id;

  delete from public.entry_items
  where entry_id = p_entry_id;

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
          and (
            (
              p.deleted_at is null
              and (
                p.category_id is not null
                or p.quick_entry_idempotency_key = v_entry_idempotency_key
              )
            )
            or (
              p.deleted_at is not null
              and exists (
                select 1
                from jsonb_array_elements(v_before -> 'items') old_item
                where old_item ->> 'product_id' = v_product_id::text
              )
            )
          )
      )
    then
      raise exception 'Produto inválido, excluído ou pendente de categoria no item %.', v_position;
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
      p_entry_id,
      v_product_id,
      v_quantity,
      v_unit_price,
      v_position
    );
  end loop;

  select jsonb_build_object(
    'entry', to_jsonb(e),
    'items', coalesce((
      select jsonb_agg(to_jsonb(ei) order by ei.position)
      from public.entry_items ei
      where ei.entry_id = e.id
    ), '[]'::jsonb)
  )
  into v_after
  from public.entries e
  where e.id = p_entry_id;

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
    v_uid,
    p_device_id,
    'UPDATE',
    'entries',
    p_entry_id,
    v_before,
    v_after
  );
end;
$$;
