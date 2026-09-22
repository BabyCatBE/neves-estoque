begin;

alter table public.products
  add column if not exists sort_order integer;

alter table public.products
  alter column unit set not null;

alter table public.products
  drop constraint if exists products_unit_v1_check,
  drop constraint if exists products_category_order_pair_check;

alter table public.products
  add constraint products_unit_v1_check
    check (unit in ('UN','KG','SC','CX','PCT','FD','BL','GL','PET','ROLO','LATA','BARRA','PT')),
  add constraint products_category_order_pair_check
    check (
      (category_id is null and sort_order is null)
      or
      (category_id is not null and sort_order is not null and sort_order >= 1)
    );

create unique index if not exists products_active_category_sort_uq
  on public.products (category_id, sort_order)
  where deleted_at is null
    and category_id is not null
    and sort_order is not null;

create or replace function private.create_product_impl(
  p_name text,
  p_category_id uuid,
  p_unit text,
  p_initial_stock_quantity numeric default null,
  p_initial_price numeric default null
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_product_id uuid;
  v_name text;
  v_unit text;
  v_next_order integer;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  v_name := regexp_replace(btrim(coalesce(p_name, '')), '[[:space:]]+', ' ', 'g');
  if char_length(v_name) < 1 or char_length(v_name) > 200 then
    raise exception 'Nome do produto inválido.';
  end if;

  if p_category_id is null then
    raise exception 'Categoria é obrigatória.';
  end if;

  perform 1
  from public.categories c
  where c.id = p_category_id
    and c.deleted_at is null
  for update;

  if not found then
    raise exception 'Categoria inválida ou excluída.';
  end if;

  v_unit := upper(btrim(coalesce(p_unit, '')));
  if v_unit not in ('UN','KG','SC','CX','PCT','FD','BL','GL','PET','ROLO','LATA','BARRA','PT') then
    raise exception 'Unidade inválida.';
  end if;

  if p_initial_stock_quantity is not null and p_initial_stock_quantity < 0 then
    raise exception 'Estoque inicial não pode ser negativo.';
  end if;

  if p_initial_price is not null and p_initial_price < 0 then
    raise exception 'Preço inicial não pode ser negativo.';
  end if;

  select coalesce(max(p.sort_order), 0) + 1
  into v_next_order
  from public.products p
  where p.category_id = p_category_id
    and p.deleted_at is null;

  insert into public.products (
    name,
    category_id,
    unit,
    sort_order,
    initial_stock_quantity,
    initial_stock_at,
    initial_price,
    initial_price_at
  )
  values (
    v_name,
    p_category_id,
    v_unit,
    v_next_order,
    p_initial_stock_quantity,
    case when p_initial_stock_quantity is null then null else now() end,
    p_initial_price,
    case when p_initial_price is null then null else now() end
  )
  returning id into v_product_id;

  return v_product_id;
end;
$$;

revoke all on function private.create_product_impl(text, uuid, text, numeric, numeric)
from public, anon, authenticated;
grant execute on function private.create_product_impl(text, uuid, text, numeric, numeric)
to authenticated;

create or replace function public.create_product(
  p_name text,
  p_category_id uuid,
  p_unit text,
  p_initial_stock_quantity numeric default null,
  p_initial_price numeric default null
)
returns uuid
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.create_product_impl(
    p_name,
    p_category_id,
    p_unit,
    p_initial_stock_quantity,
    p_initial_price
  );
$$;

revoke all on function public.create_product(text, uuid, text, numeric, numeric)
from public, anon, authenticated;
grant execute on function public.create_product(text, uuid, text, numeric, numeric)
to authenticated;

create or replace view public.stock_current
with (security_invoker = true)
as
select
  p.id as product_id,
  p.name as product_name,
  p.category_id,
  p.unit,
  (
    case
      when last_conf.quantity is null
        and p.initial_stock_quantity is null
        and entries_after.quantity is null
      then null
      else (
        coalesce(last_conf.quantity, p.initial_stock_quantity, 0::numeric)
        + coalesce(entries_after.quantity, 0::numeric)
      )
    end
  )::numeric(14,3) as current_quantity,
  last_conf.effective_at as last_conference_at,
  last_entry.effective_at as last_entry_at,
  last_entry.supplier_id as current_supplier_id,
  coalesce(last_price.unit_price, p.initial_price) as current_price,
  coalesce(last_price.effective_at, p.initial_price_at) as current_price_at,
  case
    when coalesce(last_price.unit_price, p.initial_price) is null
      or (
        last_conf.quantity is null
        and p.initial_stock_quantity is null
        and entries_after.quantity is null
      )
    then null
    else (
      (
        coalesce(last_conf.quantity, p.initial_stock_quantity, 0::numeric)
        + coalesce(entries_after.quantity, 0::numeric)
      )
      * coalesce(last_price.unit_price, p.initial_price)
    )::numeric(16,2)
  end as current_value,
  p.sort_order
from public.products p
left join lateral (
  select
    ci.quantity,
    c.effective_at
  from public.conference_items ci
  join public.conferences c on c.id = ci.conference_id
  where ci.product_id = p.id
    and c.deleted_at is null
  order by c.effective_at desc, c.created_at desc, c.id desc
  limit 1
) last_conf on true
left join lateral (
  select sum(ei.quantity)::numeric(14,3) as quantity
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id
    and e.deleted_at is null
    and e.effective_at > coalesce(
      last_conf.effective_at,
      p.initial_stock_at,
      '-infinity'::timestamptz
    )
) entries_after on true
left join lateral (
  select
    e.effective_at,
    e.supplier_id
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id
    and e.deleted_at is null
  order by e.effective_at desc, e.created_at desc, ei.position desc, ei.id desc
  limit 1
) last_entry on true
left join lateral (
  select
    ei.unit_price,
    e.effective_at
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id
    and e.deleted_at is null
    and ei.unit_price > 0
  order by e.effective_at desc, e.created_at desc, ei.position desc, ei.id desc
  limit 1
) last_price on true
where p.deleted_at is null;

revoke all on public.stock_current from anon, authenticated;
grant select on public.stock_current to authenticated;

commit;
