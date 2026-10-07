begin;

-- Preço inicial editável enquanto o Produto não possui Entrada ativa com preço real (> 0).
-- Preço em branco (NULL) e bonificação (0) não bloqueiam. Entrada excluída não bloqueia.
-- A validação ocorre no momento da gravação, com o Produto travado (FOR UPDATE):
-- inserções em entry_items adquirem FOR KEY SHARE no Produto via FK, então uma
-- Entrada concorrente é serializada antes da checagem.

create or replace function private.set_product_initial_price_impl(
  p_product_id uuid,
  p_initial_price numeric
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_current_price numeric;
  v_current_price_at timestamptz;
  v_created_at timestamptz;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_product_id is null then
    raise exception 'Produto inválido.';
  end if;

  if p_initial_price is not null and p_initial_price < 0 then
    raise exception 'Preço inicial não pode ser negativo.';
  end if;

  select p.initial_price, p.initial_price_at, p.created_at
    into v_current_price, v_current_price_at, v_created_at
  from public.products p
  where p.id = p_product_id
    and p.deleted_at is null
  for update;

  if not found then
    raise exception 'Produto não encontrado.';
  end if;

  if v_current_price is not distinct from p_initial_price then
    return;
  end if;

  if exists (
    select 1
    from public.entry_items ei
    join public.entries e on e.id = ei.entry_id
    where ei.product_id = p_product_id
      and e.deleted_at is null
      and ei.unit_price > 0
  ) then
    raise exception 'Preço inicial bloqueado: este Produto já possui Entrada com preço. Corrija o preço na Entrada correspondente.';
  end if;

  update public.products
  set
    initial_price = p_initial_price,
    initial_price_at = case
      when p_initial_price is null then null
      else coalesce(v_current_price_at, v_created_at)
    end
  where id = p_product_id;
end;
$$;

revoke all on function private.set_product_initial_price_impl(uuid, numeric)
from public, anon, authenticated;
grant execute on function private.set_product_initial_price_impl(uuid, numeric)
to authenticated;

create or replace function public.update_product_details_with_initial_price(
  p_product_id uuid,
  p_name text,
  p_category_id uuid,
  p_initial_price numeric
)
returns void
language plpgsql
security invoker
set search_path = pg_catalog, private
as $$
begin
  perform private.update_product_details_impl(p_product_id, p_name, p_category_id);
  perform private.set_product_initial_price_impl(p_product_id, p_initial_price);
end;
$$;

revoke all on function public.update_product_details_with_initial_price(uuid, text, uuid, numeric)
from public, anon, authenticated;
grant execute on function public.update_product_details_with_initial_price(uuid, text, uuid, numeric)
to authenticated;

commit;
