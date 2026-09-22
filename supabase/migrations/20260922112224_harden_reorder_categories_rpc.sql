begin;

alter function public.reorder_categories(uuid[])
  set schema private;
alter function private.reorder_categories(uuid[])
  rename to reorder_categories_impl;

revoke all on function private.reorder_categories_impl(uuid[])
  from public, anon, authenticated;
grant execute on function private.reorder_categories_impl(uuid[])
  to authenticated;

create or replace function public.reorder_categories(p_category_ids uuid[])
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.reorder_categories_impl(p_category_ids);
$$;

revoke all on function public.reorder_categories(uuid[])
  from public, anon, authenticated;
grant execute on function public.reorder_categories(uuid[])
  to authenticated;

commit;
