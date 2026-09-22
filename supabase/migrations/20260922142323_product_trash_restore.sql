begin;

create or replace function private.prepare_product_restore_order()
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
    update public.products p
    set sort_order = p.sort_order + 1
    where p.category_id = new.category_id
      and p.deleted_at is null
      and p.id <> old.id
      and p.sort_order >= v_target_order;
  end if;

  new.sort_order := v_target_order;
  return new;
end;
$$;

revoke all on function private.prepare_product_restore_order()
from public, anon, authenticated;

drop trigger if exists products_restore_order on public.products;
create trigger products_restore_order
before update on public.products
for each row
when (old.deleted_at is not null and new.deleted_at is null)
execute function private.prepare_product_restore_order();

create or replace function private.soft_delete_product_impl(p_product_id uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  update public.products
  set deleted_at = now()
  where id = p_product_id
    and deleted_at is null;

  if not found then
    raise exception 'Produto não encontrado ou já excluído.';
  end if;
end;
$$;

revoke all on function private.soft_delete_product_impl(uuid)
from public, anon, authenticated;
grant execute on function private.soft_delete_product_impl(uuid)
to authenticated;

create or replace function public.soft_delete_product(p_product_id uuid)
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.soft_delete_product_impl(p_product_id);
$$;

revoke all on function public.soft_delete_product(uuid)
from public, anon, authenticated;
grant execute on function public.soft_delete_product(uuid)
to authenticated;

create or replace function private.restore_product_impl(p_product_id uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  update public.products
  set deleted_at = null
  where id = p_product_id
    and deleted_at is not null;

  if not found then
    raise exception 'Produto não encontrado na lixeira.';
  end if;
end;
$$;

revoke all on function private.restore_product_impl(uuid)
from public, anon, authenticated;
grant execute on function private.restore_product_impl(uuid)
to authenticated;

create or replace function public.restore_product(p_product_id uuid)
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.restore_product_impl(p_product_id);
$$;

revoke all on function public.restore_product(uuid)
from public, anon, authenticated;
grant execute on function public.restore_product(uuid)
to authenticated;

commit;
