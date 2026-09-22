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
  end if;

  new.sort_order := v_target_order;
  return new;
end;
$$;

revoke all on function private.prepare_product_restore_order()
from public, anon, authenticated;
