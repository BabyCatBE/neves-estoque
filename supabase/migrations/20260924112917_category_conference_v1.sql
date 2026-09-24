begin;

alter table public.conferences
  add column if not exists scope_type text not null default 'legacy',
  add column if not exists category_id uuid references public.categories(id) on delete restrict,
  add column if not exists scope_product_id uuid references public.products(id) on delete restrict;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'conferences_scope_type_check'
      and conrelid = 'public.conferences'::regclass
  ) then
    alter table public.conferences
      add constraint conferences_scope_type_check
      check (scope_type in ('legacy', 'category', 'product'));
  end if;
end
$$;

create index if not exists conferences_category_effective_idx
  on public.conferences (category_id, effective_at desc, created_at desc)
  where deleted_at is null and scope_type = 'category';

create index if not exists conferences_scope_product_effective_idx
  on public.conferences (scope_product_id, effective_at desc, created_at desc)
  where deleted_at is null and scope_type = 'product';

create or replace function private.create_category_conference_impl(
  p_category_id uuid,
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
  v_expected_count integer;
  v_seen_ids uuid[] := '{}'::uuid[];
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;

  if p_category_id is null
    or not exists (
      select 1 from public.categories c
      where c.id = p_category_id and c.deleted_at is null
    )
  then
    raise exception 'Categoria inválida ou excluída.';
  end if;

  if p_effective_at is null then raise exception 'Data/hora efetiva da Conferência é obrigatória.'; end if;

  if char_length(btrim(coalesce(p_physical_responsible, ''))) < 1
    or char_length(btrim(coalesce(p_physical_responsible, ''))) > 160
  then
    raise exception 'Responsável físico inválido.';
  end if;

  if p_idempotency_key is null then raise exception 'Chave de idempotência é obrigatória.'; end if;

  select c.id, c.registered_by into v_conference_id, v_existing_user
  from public.conferences c
  where c.idempotency_key = p_idempotency_key
  limit 1;

  if v_conference_id is not null then
    if v_existing_user <> v_uid then raise exception 'Chave de idempotência já utilizada.'; end if;
    return v_conference_id;
  end if;

  select count(*) into v_expected_count
  from public.products p
  where p.category_id = p_category_id and p.deleted_at is null;

  if v_expected_count = 0 then raise exception 'A categoria não possui produtos ativos para conferir.'; end if;

  if p_items is null
    or jsonb_typeof(p_items) <> 'array'
    or jsonb_array_length(p_items) <> v_expected_count
  then
    raise exception 'A Conferência da categoria precisa conter todos os produtos ativos.';
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  insert into public.conferences (
    effective_at, physical_responsible, observation, registered_by, device_id,
    idempotency_key, scope_type, category_id, scope_product_id
  )
  values (
    p_effective_at, btrim(p_physical_responsible),
    nullif(btrim(coalesce(p_observation, '')), ''), v_uid, p_device_id,
    p_idempotency_key, 'category', p_category_id, null
  )
  returning id into v_conference_id;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_position := v_position + 1;
    begin
      v_product_id := nullif(v_item ->> 'product_id', '')::uuid;
      v_quantity := nullif(v_item ->> 'quantity', '')::numeric;
    exception when others then
      raise exception 'Item % possui formato inválido.', v_position;
    end;

    if v_product_id is null
      or not exists (
        select 1 from public.products p
        where p.id = v_product_id
          and p.category_id = p_category_id
          and p.deleted_at is null
      )
    then
      raise exception 'Produto inválido, excluído ou fora da categoria no item %.', v_position;
    end if;

    if v_product_id = any(v_seen_ids) then
      raise exception 'Produto repetido na Conferência no item %.', v_position;
    end if;
    v_seen_ids := array_append(v_seen_ids, v_product_id);

    if v_quantity is null or v_quantity < 0 then
      raise exception 'Quantidade não pode ser negativa no item %.', v_position;
    end if;

    insert into public.conference_items (conference_id, product_id, quantity, position)
    values (v_conference_id, v_product_id, v_quantity, v_position);
  end loop;

  if cardinality(v_seen_ids) <> v_expected_count then
    raise exception 'A Conferência da categoria precisa conter todos os produtos ativos.';
  end if;

  return v_conference_id;
end;
$$;

create or replace function private.update_category_conference_impl(
  p_conference_id uuid,
  p_effective_at timestamptz,
  p_physical_responsible text,
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
  v_expected_count integer;
  v_seen_ids uuid[] := '{}'::uuid[];
  v_before jsonb;
  v_after jsonb;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;

  if p_conference_id is null
    or not exists (
      select 1 from public.conferences c
      where c.id = p_conference_id
        and c.deleted_at is null
        and c.scope_type = 'category'
        and c.category_id is not null
    )
  then
    raise exception 'Conferência de categoria não encontrada.';
  end if;

  if p_effective_at is null then raise exception 'Data/hora efetiva da Conferência é obrigatória.'; end if;

  if char_length(btrim(coalesce(p_physical_responsible, ''))) < 1
    or char_length(btrim(coalesce(p_physical_responsible, ''))) > 160
  then
    raise exception 'Responsável físico inválido.';
  end if;

  select count(*) into v_expected_count
  from public.conference_items ci
  where ci.conference_id = p_conference_id;

  if v_expected_count = 0 then raise exception 'A Conferência não possui itens para corrigir.'; end if;

  if p_items is null
    or jsonb_typeof(p_items) <> 'array'
    or jsonb_array_length(p_items) <> v_expected_count
  then
    raise exception 'A correção precisa manter todos os produtos da Conferência original.';
  end if;

  select jsonb_build_object(
    'conference', to_jsonb(c),
    'items', coalesce((
      select jsonb_agg(to_jsonb(ci) order by ci.position)
      from public.conference_items ci where ci.conference_id = c.id
    ), '[]'::jsonb)
  )
  into v_before
  from public.conferences c
  where c.id = p_conference_id
  for update;

  perform set_config('app.device_id', p_device_id::text, true);
  perform set_config('app.suppress_row_audit', 'on', true);

  update public.conferences
  set effective_at = p_effective_at,
      physical_responsible = btrim(p_physical_responsible),
      observation = nullif(btrim(coalesce(p_observation, '')), '')
  where id = p_conference_id;

  delete from public.conference_items where conference_id = p_conference_id;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_position := v_position + 1;
    begin
      v_product_id := nullif(v_item ->> 'product_id', '')::uuid;
      v_quantity := nullif(v_item ->> 'quantity', '')::numeric;
    exception when others then
      raise exception 'Item % possui formato inválido.', v_position;
    end;

    if v_product_id is null
      or not exists (
        select 1
        from jsonb_array_elements(v_before -> 'items') old_item
        where old_item ->> 'product_id' = v_product_id::text
      )
    then
      raise exception 'A correção não pode alterar os produtos da Conferência original.';
    end if;

    if v_product_id = any(v_seen_ids) then
      raise exception 'Produto repetido na Conferência no item %.', v_position;
    end if;
    v_seen_ids := array_append(v_seen_ids, v_product_id);

    if v_quantity is null or v_quantity < 0 then
      raise exception 'Quantidade não pode ser negativa no item %.', v_position;
    end if;

    insert into public.conference_items (conference_id, product_id, quantity, position)
    values (p_conference_id, v_product_id, v_quantity, v_position);
  end loop;

  if cardinality(v_seen_ids) <> v_expected_count then
    raise exception 'A correção precisa manter todos os produtos da Conferência original.';
  end if;

  select jsonb_build_object(
    'conference', to_jsonb(c),
    'items', coalesce((
      select jsonb_agg(to_jsonb(ci) order by ci.position)
      from public.conference_items ci where ci.conference_id = c.id
    ), '[]'::jsonb)
  )
  into v_after
  from public.conferences c
  where c.id = p_conference_id;

  insert into public.audit_log (
    actor_user_id, device_id, action, entity_type, entity_id, before_data, after_data
  )
  values (
    v_uid, p_device_id, 'UPDATE', 'conferences', p_conference_id, v_before, v_after
  );
end;
$$;

revoke all on function private.create_category_conference_impl(
  uuid, timestamptz, text, uuid, uuid, jsonb, text
) from public, anon, authenticated, service_role;
revoke all on function private.update_category_conference_impl(
  uuid, timestamptz, text, uuid, jsonb, text
) from public, anon, authenticated, service_role;

grant execute on function private.create_category_conference_impl(
  uuid, timestamptz, text, uuid, uuid, jsonb, text
) to authenticated, service_role;
grant execute on function private.update_category_conference_impl(
  uuid, timestamptz, text, uuid, jsonb, text
) to authenticated, service_role;

create or replace function public.create_category_conference(
  p_category_id uuid,
  p_effective_at timestamptz,
  p_physical_responsible text,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_items jsonb,
  p_observation text default null
)
returns uuid
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.create_category_conference_impl(
    p_category_id, p_effective_at, p_physical_responsible, p_device_id,
    p_idempotency_key, p_items, p_observation
  );
$$;

create or replace function public.update_category_conference(
  p_conference_id uuid,
  p_effective_at timestamptz,
  p_physical_responsible text,
  p_device_id uuid,
  p_items jsonb,
  p_observation text default null
)
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.update_category_conference_impl(
    p_conference_id, p_effective_at, p_physical_responsible, p_device_id,
    p_items, p_observation
  );
$$;

revoke all on function public.create_category_conference(
  uuid, timestamptz, text, uuid, uuid, jsonb, text
) from public, anon, authenticated, service_role;
revoke all on function public.update_category_conference(
  uuid, timestamptz, text, uuid, jsonb, text
) from public, anon, authenticated, service_role;

grant execute on function public.create_category_conference(
  uuid, timestamptz, text, uuid, uuid, jsonb, text
) to authenticated, service_role;
grant execute on function public.update_category_conference(
  uuid, timestamptz, text, uuid, jsonb, text
) to authenticated, service_role;

revoke execute on function public.create_conference(
  timestamptz, text, uuid, uuid, jsonb, text
) from authenticated;
revoke execute on function private.create_conference_impl(
  timestamptz, text, uuid, uuid, jsonb, text
) from authenticated;

grant execute on function public.create_conference(
  timestamptz, text, uuid, uuid, jsonb, text
) to service_role;
grant execute on function private.create_conference_impl(
  timestamptz, text, uuid, uuid, jsonb, text
) to service_role;

commit;
