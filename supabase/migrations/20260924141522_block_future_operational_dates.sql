create or replace function private.reject_future_effective_date()
returns trigger
language plpgsql
set search_path = pg_catalog, public, private
as $$
declare
  v_effective_date date;
  v_today date := (clock_timestamp() at time zone 'America/Bahia')::date;
begin
  v_effective_date := (new.effective_at at time zone 'America/Bahia')::date;

  if v_effective_date > v_today then
    if tg_table_name = 'entries' then
      raise exception 'A data da Entrada não pode ser futura.';
    elsif tg_table_name = 'conferences' then
      raise exception 'A data da Conferência não pode ser futura.';
    else
      raise exception 'A data efetiva não pode ser futura.';
    end if;
  end if;

  return new;
end;
$$;

revoke all on function private.reject_future_effective_date()
from public, anon, authenticated, service_role;

drop trigger if exists entries_reject_future_effective_date on public.entries;
create trigger entries_reject_future_effective_date
before insert or update of effective_at on public.entries
for each row execute function private.reject_future_effective_date();

drop trigger if exists conferences_reject_future_effective_date on public.conferences;
create trigger conferences_reject_future_effective_date
before insert or update of effective_at on public.conferences
for each row execute function private.reject_future_effective_date();

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
    and (c.effective_at at time zone 'America/Bahia')::date
      <= (clock_timestamp() at time zone 'America/Bahia')::date
  order by c.effective_at desc, c.created_at desc, c.id desc
  limit 1
) last_conf on true
left join lateral (
  select sum(ei.quantity)::numeric(14,3) as quantity
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
  select
    e.effective_at,
    e.supplier_id
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
  select
    ei.unit_price,
    e.effective_at
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

revoke all on public.stock_current from anon, authenticated;
grant select on public.stock_current to authenticated;
