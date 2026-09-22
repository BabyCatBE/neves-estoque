create or replace function public.reorder_categories(p_category_ids uuid[])
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_active_count integer;
  v_distinct_count integer;
  v_updated_count integer;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_category_ids is null or cardinality(p_category_ids) = 0 then
    raise exception 'Informe a ordem das categorias.';
  end if;

  select count(*)
    into v_active_count
  from public.categories
  where deleted_at is null;

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

  update public.categories c
  set sort_order = ordered.position::integer
  from unnest(p_category_ids) with ordinality as ordered(category_id, position)
  where c.id = ordered.category_id
    and c.deleted_at is null;

  get diagnostics v_updated_count = row_count;

  if v_updated_count <> v_active_count then
    raise exception 'Não foi possível atualizar toda a ordem das categorias.';
  end if;
end;
$$;

revoke all on function public.reorder_categories(uuid[]) from public, anon;
grant execute on function public.reorder_categories(uuid[]) to authenticated;
