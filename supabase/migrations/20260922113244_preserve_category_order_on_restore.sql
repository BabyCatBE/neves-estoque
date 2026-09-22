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
    update public.categories c
    set sort_order = c.sort_order + 1
    where c.deleted_at is null
      and c.id <> old.id
      and c.sort_order >= v_target_order;
  end if;

  new.sort_order := v_target_order;
  return new;
end;
$$;

revoke all on function private.prepare_category_restore_order()
from public, anon, authenticated;

drop trigger if exists categories_restore_order on public.categories;

create trigger categories_restore_order
before update on public.categories
for each row
when (old.deleted_at is not null and new.deleted_at is null)
execute function private.prepare_category_restore_order();
