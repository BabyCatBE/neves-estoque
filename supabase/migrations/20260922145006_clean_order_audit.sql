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

create or replace function private.reorder_categories_impl(p_category_ids uuid[])
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_active_count integer;
  v_distinct_count integer;
  v_updated_count integer;
  v_before_ids uuid[];
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_category_ids is null or cardinality(p_category_ids) = 0 then
    raise exception 'Informe a ordem das categorias.';
  end if;

  select count(*), array_agg(c.id order by c.sort_order nulls last, c.name, c.id)
    into v_active_count, v_before_ids
  from public.categories c
  where c.deleted_at is null;

  select count(distinct category_id)
    into v_distinct_count
  from unnest(p_category_ids) as category_id;

  if cardinality(p_category_ids) <> v_active_count
     or v_distinct_count <> v_active_count then
    raise exception 'A ordem deve conter todas as categorias ativas, sem duplicidades.';
  end if;

  if exists (
    select 1
    from unnest(p_category_ids) as category_id
    left join public.categories c
      on c.id = category_id
     and c.deleted_at is null
    where c.id is null
  ) then
    raise exception 'A ordem contém uma categoria inválida ou excluída.';
  end if;

  if v_before_ids = p_category_ids then
    return;
  end if;

  begin
    perform set_config('app.suppress_row_audit', 'on', true);

    update public.categories c
    set sort_order = ordered.position::integer
    from unnest(p_category_ids) with ordinality as ordered(category_id, position)
    where c.id = ordered.category_id
      and c.deleted_at is null;

    get diagnostics v_updated_count = row_count;

    perform set_config('app.suppress_row_audit', 'off', true);
  exception
    when others then
      perform set_config('app.suppress_row_audit', 'off', true);
      raise;
  end;

  if v_updated_count <> v_active_count then
    raise exception 'Não foi possível atualizar toda a ordem das categorias.';
  end if;

  insert into public.audit_log (
    actor_user_id,
    device_id,
    action,
    entity_type,
    entity_id,
    before_data,
    after_data,
    metadata
  )
  values (
    auth.uid(),
    private.request_device_id(),
    'REORDER',
    'category_order',
    null,
    jsonb_build_object('category_ids', to_jsonb(v_before_ids)),
    jsonb_build_object('category_ids', to_jsonb(p_category_ids)),
    jsonb_build_object('changed_count', v_updated_count)
  );
end;
$$;

revoke all on function private.reorder_categories_impl(uuid[])
from public, anon, authenticated;
grant execute on function private.reorder_categories_impl(uuid[])
to authenticated;

create or replace function private.reorder_products_impl(p_orders jsonb)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_order jsonb;
  v_category_id uuid;
  v_product_ids uuid[];
  v_before_ids uuid[];
  v_active_count integer;
  v_distinct_count integer;
  v_updated_count integer;
  v_offset integer;
  v_seen_categories uuid[] := array[]::uuid[];
  v_before_payload jsonb := '[]'::jsonb;
  v_after_payload jsonb := '[]'::jsonb;
  v_changed_categories integer := 0;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_orders is null or jsonb_typeof(p_orders) <> 'array' then
    raise exception 'Informe uma lista válida de ordens.';
  end if;

  if jsonb_array_length(p_orders) = 0 then
    return;
  end if;

  for v_order in
    select value
    from jsonb_array_elements(p_orders)
  loop
    begin
      v_category_id := (v_order ->> 'category_id')::uuid;
    exception
      when others then
        raise exception 'Categoria inválida na reordenação.';
    end;

    if v_category_id = any(v_seen_categories) then
      raise exception 'A mesma categoria não pode aparecer duas vezes na reordenação.';
    end if;
    v_seen_categories := array_append(v_seen_categories, v_category_id);

    perform 1
    from public.categories c
    where c.id = v_category_id
      and c.deleted_at is null
    for update;

    if not found then
      raise exception 'Categoria inválida ou excluída.';
    end if;

    if jsonb_typeof(v_order -> 'product_ids') <> 'array' then
      raise exception 'Ordem de produtos inválida.';
    end if;

    select array_agg(x.value::uuid order by x.ordinality)
      into v_product_ids
    from jsonb_array_elements_text(v_order -> 'product_ids')
      with ordinality as x(value, ordinality);

    select
      count(*),
      array_agg(p.id order by p.sort_order nulls last, p.name, p.id)
      into v_active_count, v_before_ids
    from public.products p
    where p.category_id = v_category_id
      and p.deleted_at is null;

    if v_active_count = 0 then
      if coalesce(cardinality(v_product_ids), 0) <> 0 then
        raise exception 'A categoria não possui produtos ativos.';
      end if;
      continue;
    end if;

    if v_product_ids is null or cardinality(v_product_ids) <> v_active_count then
      raise exception 'A ordem deve conter todos os produtos ativos da categoria.';
    end if;

    select count(distinct product_id)
      into v_distinct_count
    from unnest(v_product_ids) as product_id;

    if v_distinct_count <> v_active_count then
      raise exception 'A ordem de produtos contém duplicidades.';
    end if;

    if exists (
      select 1
      from unnest(v_product_ids) as product_id
      left join public.products p
        on p.id = product_id
       and p.category_id = v_category_id
       and p.deleted_at is null
      where p.id is null
    ) then
      raise exception 'A ordem contém produto inválido, excluído ou de outra categoria.';
    end if;

    if v_before_ids = v_product_ids then
      continue;
    end if;

    v_before_payload := v_before_payload || jsonb_build_array(
      jsonb_build_object(
        'category_id', v_category_id,
        'product_ids', to_jsonb(v_before_ids)
      )
    );

    v_after_payload := v_after_payload || jsonb_build_array(
      jsonb_build_object(
        'category_id', v_category_id,
        'product_ids', to_jsonb(v_product_ids)
      )
    );

    select coalesce(max(p.sort_order), 0) + v_active_count + 1000
      into v_offset
    from public.products p
    where p.category_id = v_category_id
      and p.deleted_at is null;

    begin
      perform set_config('app.suppress_row_audit', 'on', true);

      update public.products p
      set sort_order = p.sort_order + v_offset
      where p.category_id = v_category_id
        and p.deleted_at is null;

      update public.products p
      set sort_order = ordered.position::integer
      from unnest(v_product_ids) with ordinality as ordered(product_id, position)
      where p.id = ordered.product_id
        and p.category_id = v_category_id
        and p.deleted_at is null;

      get diagnostics v_updated_count = row_count;

      perform set_config('app.suppress_row_audit', 'off', true);
    exception
      when others then
        perform set_config('app.suppress_row_audit', 'off', true);
        raise;
    end;

    if v_updated_count <> v_active_count then
      raise exception 'Não foi possível atualizar toda a ordem dos produtos.';
    end if;

    v_changed_categories := v_changed_categories + 1;
  end loop;

  if v_changed_categories > 0 then
    insert into public.audit_log (
      actor_user_id,
      device_id,
      action,
      entity_type,
      entity_id,
      before_data,
      after_data,
      metadata
    )
    values (
      auth.uid(),
      private.request_device_id(),
      'REORDER',
      'product_order',
      null,
      jsonb_build_object('categories', v_before_payload),
      jsonb_build_object('categories', v_after_payload),
      jsonb_build_object('changed_categories', v_changed_categories)
    );
  end if;
end;
$$;

revoke all on function private.reorder_products_impl(jsonb)
from public, anon, authenticated;
grant execute on function private.reorder_products_impl(jsonb)
to authenticated;

create or replace function private.prepare_category_restore_order()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_target_order integer;
  v_next_order integer;
begin
  if not (old.deleted_at is not null and new.deleted_at is null) then
    return new;
  end if;

  v_target_order := old.sort_order;

  if v_target_order is null then
    select coalesce(max(c.sort_order), 0) + 1
      into v_next_order
    from public.categories c
    where c.deleted_at is null
      and c.id <> old.id;

    new.sort_order := v_next_order;
    return new;
  end if;

  if exists (
    select 1
    from public.categories c
    where c.deleted_at is null
      and c.id <> old.id
      and c.sort_order = v_target_order
  ) then
    begin
      perform set_config('app.suppress_row_audit', 'on', true);

      update public.categories c
      set sort_order = c.sort_order + 1
      where c.deleted_at is null
        and c.id <> old.id
        and c.sort_order >= v_target_order;

      perform set_config('app.suppress_row_audit', 'off', true);
    exception
      when others then
        perform set_config('app.suppress_row_audit', 'off', true);
        raise;
    end;
  end if;

  new.sort_order := v_target_order;
  return new;
end;
$$;

revoke all on function private.prepare_category_restore_order()
from public, anon, authenticated;

create or replace function private.prepare_product_restore_order()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_target_order integer;
  v_next_order integer;
  v_offset integer;
begin
  if not (old.deleted_at is not null and new.deleted_at is null) then
    return new;
  end if;

  if new.category_id is null then
    new.sort_order := null;
    return new;
  end if;

  v_target_order := old.sort_order;

  if v_target_order is null then
    select coalesce(max(p.sort_order), 0) + 1
      into v_next_order
    from public.products p
    where p.category_id = new.category_id
      and p.deleted_at is null
      and p.id <> old.id;

    new.sort_order := v_next_order;
    return new;
  end if;

  if exists (
    select 1
    from public.products p
    where p.category_id = new.category_id
      and p.deleted_at is null
      and p.id <> old.id
      and p.sort_order = v_target_order
  ) then
    select coalesce(max(p.sort_order), 0)
           + count(*)::integer
           + 1000
      into v_offset
    from public.products p
    where p.category_id = new.category_id
      and p.deleted_at is null
      and p.id <> old.id;

    begin
      perform set_config('app.suppress_row_audit', 'on', true);

      update public.products p
      set sort_order = p.sort_order + v_offset
      where p.category_id = new.category_id
        and p.deleted_at is null
        and p.id <> old.id
        and p.sort_order >= v_target_order;

      update public.products p
      set sort_order = p.sort_order - v_offset + 1
      where p.category_id = new.category_id
        and p.deleted_at is null
        and p.id <> old.id
        and p.sort_order >= v_target_order + v_offset;

      perform set_config('app.suppress_row_audit', 'off', true);
    exception
      when others then
        perform set_config('app.suppress_row_audit', 'off', true);
        raise;
    end;
  end if;

  new.sort_order := v_target_order;
  return new;
end;
$$;

revoke all on function private.prepare_product_restore_order()
from public, anon, authenticated;

commit;
