begin;

create or replace function private.update_product_details_impl(
  p_product_id uuid,
  p_name text,
  p_category_id uuid
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_name text;
  v_old_category_id uuid;
  v_old_sort_order integer;
  v_next_order integer;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_product_id is null then
    raise exception 'Produto inválido.';
  end if;

  v_name := regexp_replace(btrim(coalesce(p_name, '')), '[[:space:]]+', ' ', 'g');
  if char_length(v_name) < 1 or char_length(v_name) > 200 then
    raise exception 'Nome do produto inválido.';
  end if;

  if p_category_id is null then
    raise exception 'Categoria é obrigatória.';
  end if;

  select p.category_id, p.sort_order
    into v_old_category_id, v_old_sort_order
  from public.products p
  where p.id = p_product_id
    and p.deleted_at is null
  for update;

  if not found then
    raise exception 'Produto não encontrado.';
  end if;

  perform 1
  from public.categories c
  where c.id = p_category_id
    and c.deleted_at is null
  for update;

  if not found then
    raise exception 'Categoria inválida ou excluída.';
  end if;

  if v_old_category_id = p_category_id then
    update public.products
    set name = v_name
    where id = p_product_id;
    return;
  end if;

  select coalesce(max(p.sort_order), 0) + 1
    into v_next_order
  from public.products p
  where p.category_id = p_category_id
    and p.deleted_at is null;

  update public.products
  set
    name = v_name,
    category_id = p_category_id,
    sort_order = v_next_order
  where id = p_product_id;
end;
$$;

revoke all on function private.update_product_details_impl(uuid, text, uuid)
from public, anon, authenticated;
grant execute on function private.update_product_details_impl(uuid, text, uuid)
to authenticated;

create or replace function public.update_product_details(
  p_product_id uuid,
  p_name text,
  p_category_id uuid
)
returns void
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.update_product_details_impl(
    p_product_id,
    p_name,
    p_category_id
  );
$$;

revoke all on function public.update_product_details(uuid, text, uuid)
from public, anon, authenticated;
grant execute on function public.update_product_details(uuid, text, uuid)
to authenticated;

commit;
