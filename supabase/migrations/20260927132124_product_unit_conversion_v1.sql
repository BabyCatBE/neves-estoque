begin;

drop view public.stock_current;

alter table public.products
  alter column initial_stock_quantity type numeric using initial_stock_quantity::numeric,
  alter column initial_price type numeric using initial_price::numeric;

alter table public.entry_items
  alter column quantity type numeric using quantity::numeric,
  alter column unit_price type numeric using unit_price::numeric;

alter table public.conference_items
  alter column quantity type numeric using quantity::numeric;

create or replace view public.stock_current
with (security_invoker = true)
as
select
  p.id as product_id,
  p.name as product_name,
  p.category_id,
  p.unit,
  case
    when last_conf.quantity is null
      and p.initial_stock_quantity is null
      and entries_after.quantity is null
    then null
    else
      coalesce(last_conf.quantity, p.initial_stock_quantity, 0::numeric)
      + coalesce(entries_after.quantity, 0::numeric)
  end as current_quantity,
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
  select ci.quantity, c.effective_at
  from public.conference_items ci
  join public.conferences c on c.id = ci.conference_id
  where ci.product_id = p.id
    and c.deleted_at is null
    and (c.effective_at at time zone 'America/Bahia')::date
      <= (clock_timestamp() at time zone 'America/Bahia')::date
  order by c.effective_at desc, c.created_at desc, c.id desc
  limit 1
) last_conf on true
left join lateral (
  select sum(ei.quantity) as quantity
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id
    and e.deleted_at is null
    and (e.effective_at at time zone 'America/Bahia')::date
      <= (clock_timestamp() at time zone 'America/Bahia')::date
    and e.effective_at > coalesce(
      last_conf.effective_at,
      p.initial_stock_at,
      '-infinity'::timestamptz
    )
) entries_after on true
left join lateral (
  select e.effective_at, e.supplier_id
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id
    and e.deleted_at is null
    and (e.effective_at at time zone 'America/Bahia')::date
      <= (clock_timestamp() at time zone 'America/Bahia')::date
  order by e.effective_at desc, e.created_at desc, ei.position desc, ei.id desc
  limit 1
) last_entry on true
left join lateral (
  select ei.unit_price, e.effective_at
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id
    and e.deleted_at is null
    and (e.effective_at at time zone 'America/Bahia')::date
      <= (clock_timestamp() at time zone 'America/Bahia')::date
    and ei.unit_price > 0
  order by e.effective_at desc, e.created_at desc, ei.position desc, ei.id desc
  limit 1
) last_price on true
where p.deleted_at is null;

revoke all on table public.stock_current from public, anon, authenticated, service_role;
grant select on table public.stock_current to authenticated, service_role;

create table if not exists private.product_unit_conversion_backups (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null,
  old_unit text not null,
  new_unit text not null,
  old_quantity numeric not null,
  new_quantity numeric not null,
  factor numeric not null,
  product_before jsonb not null,
  entry_items_before jsonb not null default '[]'::jsonb,
  conference_items_before jsonb not null default '[]'::jsonb,
  created_by uuid,
  device_id uuid,
  created_at timestamptz not null default now()
);

revoke all on table private.product_unit_conversion_backups
from public, anon, authenticated, service_role;

create index if not exists product_unit_conversion_backups_product_idx
  on private.product_unit_conversion_backups (product_id, created_at desc);

create or replace function private.convert_product_unit_impl(
  p_product_id uuid,
  p_new_unit text,
  p_old_quantity numeric,
  p_new_quantity numeric,
  p_device_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_old_unit text;
  v_new_unit text;
  v_factor numeric;
  v_backup_id uuid;
  v_product_before jsonb;
  v_product_after jsonb;
  v_entry_items_before jsonb;
  v_conference_items_before jsonb;
  v_entry_count integer;
  v_conference_count integer;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;
  if p_product_id is null then raise exception 'Produto inválido.'; end if;

  if p_old_quantity is null or p_old_quantity <= 0
     or p_new_quantity is null or p_new_quantity <= 0 then
    raise exception 'A equivalência deve usar quantidades maiores que zero.';
  end if;

  v_new_unit := upper(btrim(coalesce(p_new_unit, '')));
  if v_new_unit not in ('UN','KG','SC','CX','PCT','FD','BL','GL','PET','ROLO','LATA','BARRA','PT') then
    raise exception 'Unidade inválida.';
  end if;

  select p.unit, to_jsonb(p)
    into v_old_unit, v_product_before
  from public.products p
  where p.id = p_product_id and p.deleted_at is null
  for update;

  if not found then raise exception 'Produto não encontrado.'; end if;
  if v_old_unit = v_new_unit then raise exception 'Escolha uma unidade diferente da atual.'; end if;

  v_factor := p_new_quantity / p_old_quantity;

  lock table public.entry_items in share row exclusive mode;
  lock table public.conference_items in share row exclusive mode;

  select coalesce(jsonb_agg(to_jsonb(ei) order by ei.entry_id, ei.position, ei.id), '[]'::jsonb),
         count(*)::integer
    into v_entry_items_before, v_entry_count
  from public.entry_items ei
  where ei.product_id = p_product_id;

  select coalesce(jsonb_agg(to_jsonb(ci) order by ci.conference_id, ci.position, ci.id), '[]'::jsonb),
         count(*)::integer
    into v_conference_items_before, v_conference_count
  from public.conference_items ci
  where ci.product_id = p_product_id;

  insert into private.product_unit_conversion_backups (
    product_id, old_unit, new_unit, old_quantity, new_quantity, factor,
    product_before, entry_items_before, conference_items_before, created_by, device_id
  )
  values (
    p_product_id, v_old_unit, v_new_unit, p_old_quantity, p_new_quantity, v_factor,
    v_product_before, v_entry_items_before, v_conference_items_before, v_uid, p_device_id
  )
  returning id into v_backup_id;

  perform set_config('app.device_id', p_device_id::text, true);
  perform set_config('app.suppress_row_audit', 'on', true);

  begin
    update public.products
    set
      unit = v_new_unit,
      initial_stock_quantity = case when initial_stock_quantity is null then null else initial_stock_quantity * v_factor end,
      initial_price = case when initial_price is null then null else initial_price / v_factor end
    where id = p_product_id;

    update public.entry_items
    set
      quantity = quantity * v_factor,
      unit_price = case when unit_price is null then null else unit_price / v_factor end
    where product_id = p_product_id;

    update public.conference_items
    set quantity = quantity * v_factor
    where product_id = p_product_id;

    perform set_config('app.suppress_row_audit', 'off', true);
  exception
    when others then
      perform set_config('app.suppress_row_audit', 'off', true);
      raise;
  end;

  select to_jsonb(p) into v_product_after
  from public.products p where p.id = p_product_id;

  insert into public.audit_log (
    actor_user_id, device_id, action, entity_type, entity_id, before_data, after_data, metadata
  )
  values (
    v_uid, p_device_id, 'UNIT_CONVERSION', 'products', p_product_id,
    jsonb_build_object('product', v_product_before, 'entry_items_count', v_entry_count, 'conference_items_count', v_conference_count),
    jsonb_build_object('product', v_product_after, 'entry_items_count', v_entry_count, 'conference_items_count', v_conference_count),
    jsonb_build_object(
      'backup_id', v_backup_id, 'old_unit', v_old_unit, 'new_unit', v_new_unit,
      'old_quantity', p_old_quantity, 'new_quantity', p_new_quantity, 'factor', v_factor
    )
  );

  return jsonb_build_object(
    'backup_id', v_backup_id, 'old_unit', v_old_unit, 'new_unit', v_new_unit,
    'old_quantity', p_old_quantity, 'new_quantity', p_new_quantity, 'factor', v_factor,
    'entry_items_count', v_entry_count, 'conference_items_count', v_conference_count
  );
end;
$$;

revoke all on function private.convert_product_unit_impl(uuid, text, numeric, numeric, uuid)
from public, anon, authenticated, service_role;
grant execute on function private.convert_product_unit_impl(uuid, text, numeric, numeric, uuid)
to authenticated, service_role;

create or replace function public.convert_product_unit(
  p_product_id uuid,
  p_new_unit text,
  p_old_quantity numeric,
  p_new_quantity numeric,
  p_device_id uuid
)
returns jsonb
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.convert_product_unit_impl(
    p_product_id, p_new_unit, p_old_quantity, p_new_quantity, p_device_id
  );
$$;

revoke all on function public.convert_product_unit(uuid, text, numeric, numeric, uuid)
from public, anon, authenticated, service_role;
grant execute on function public.convert_product_unit(uuid, text, numeric, numeric, uuid)
to authenticated, service_role;

commit;
