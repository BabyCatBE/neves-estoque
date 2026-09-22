begin;

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
  v_active_count integer;
  v_distinct_count integer;
  v_updated_count integer;
  v_offset integer;
  v_seen_categories uuid[] := array[]::uuid[];
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

    select count(*)
      into v_active_count
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

    select coalesce(max(p.sort_order), 0) + v_active_count + 1000
      into v_offset
    from public.products p
    where p.category_id = v_category_id
      and p.deleted_at is null;

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

    if v_updated_count <> v_active_count then
      raise exception 'Não foi possível atualizar toda a ordem dos produtos.';
    end if;
  end loop;
end;
$$;

revoke all on function private.reorder_products_impl(jsonb)
from public, anon, authenticated;
grant execute on function private.reorder_products_impl(jsonb)
to authenticated;

create or replace function public.reorder_products(p_orders jsonb)
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.reorder_products_impl(p_orders);
$$;

revoke all on function public.reorder_products(jsonb)
from public, anon, authenticated;
grant execute on function public.reorder_products(jsonb)
to authenticated;

commit;
