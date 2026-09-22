create or replace function private.prevent_nonempty_category_delete()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
  if old.deleted_at is null and new.deleted_at is not null then
    if exists (
      select 1
      from public.products p
      where p.category_id = old.id
        and p.deleted_at is null
    ) then
      raise exception 'Categoria possui produtos ativos e não pode ser excluída.';
    end if;
  end if;

  return new;
end;
$$;

revoke all on function private.prevent_nonempty_category_delete() from public;
revoke all on function private.prevent_nonempty_category_delete() from anon;
revoke all on function private.prevent_nonempty_category_delete() from authenticated;

drop trigger if exists categories_guard_nonempty_delete on public.categories;

create trigger categories_guard_nonempty_delete
before update on public.categories
for each row
execute function private.prevent_nonempty_category_delete();
