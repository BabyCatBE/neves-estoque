begin;

alter table public.products
  add column if not exists stock_reconfirmation_required_at timestamptz;

drop view public.stock_current;

create view public.stock_current
with (security_invoker = true)
as
select
  p.id as product_id,
  p.name as product_name,
  p.category_id,
  p.unit,
  case
    when p.stock_reconfirmation_required_at is not null then null::numeric
    when last_conf.quantity is null and p.initial_stock_quantity is null and entries_after.quantity is null then null::numeric
    else coalesce(last_conf.quantity, p.initial_stock_quantity, 0::numeric) + coalesce(entries_after.quantity, 0::numeric)
  end as current_quantity,
  last_conf.effective_at as last_conference_at,
  last_entry.effective_at as last_entry_at,
  last_entry.supplier_id as current_supplier_id,
  coalesce(last_price.unit_price, p.initial_price) as current_price,
  coalesce(last_price.effective_at, p.initial_price_at) as current_price_at,
  case
    when p.stock_reconfirmation_required_at is not null then null::numeric
    when coalesce(last_price.unit_price, p.initial_price) is null
      or (last_conf.quantity is null and p.initial_stock_quantity is null and entries_after.quantity is null)
    then null::numeric
    else ((coalesce(last_conf.quantity, p.initial_stock_quantity, 0::numeric) + coalesce(entries_after.quantity, 0::numeric)) * coalesce(last_price.unit_price, p.initial_price))::numeric(16,2)
  end as current_value,
  p.sort_order,
  (p.stock_reconfirmation_required_at is not null) as stock_requires_conference
from public.products p
left join lateral (
  select ci.quantity, c.effective_at
  from public.conference_items ci
  join public.conferences c on c.id = ci.conference_id
  where ci.product_id = p.id and c.deleted_at is null
    and (c.effective_at at time zone 'America/Bahia')::date <= (clock_timestamp() at time zone 'America/Bahia')::date
  order by c.effective_at desc, c.created_at desc, c.id desc
  limit 1
) last_conf on true
left join lateral (
  select sum(ei.quantity) as quantity
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id and e.deleted_at is null
    and (e.effective_at at time zone 'America/Bahia')::date <= (clock_timestamp() at time zone 'America/Bahia')::date
    and e.effective_at > coalesce(last_conf.effective_at, p.initial_stock_at, '-infinity'::timestamptz)
) entries_after on true
left join lateral (
  select e.effective_at, e.supplier_id
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id and e.deleted_at is null
    and (e.effective_at at time zone 'America/Bahia')::date <= (clock_timestamp() at time zone 'America/Bahia')::date
  order by e.effective_at desc, e.created_at desc, ei.position desc, ei.id desc
  limit 1
) last_entry on true
left join lateral (
  select ei.unit_price, e.effective_at
  from public.entry_items ei
  join public.entries e on e.id = ei.entry_id
  where ei.product_id = p.id and e.deleted_at is null
    and (e.effective_at at time zone 'America/Bahia')::date <= (clock_timestamp() at time zone 'America/Bahia')::date
    and ei.unit_price > 0
  order by e.effective_at desc, e.created_at desc, ei.position desc, ei.id desc
  limit 1
) last_price on true
where p.deleted_at is null;

revoke all on table public.stock_current from public, anon, authenticated, service_role;
grant select on table public.stock_current to authenticated, service_role;

create table if not exists private.product_merge_backups (
  id uuid primary key default gen_random_uuid(),
  survivor_product_id uuid not null,
  absorbed_product_id uuid not null,
  survivor_before jsonb not null,
  absorbed_before jsonb not null,
  entry_items_before jsonb not null default '[]'::jsonb,
  conference_items_before jsonb not null default '[]'::jsonb,
  scope_conferences_before jsonb not null default '[]'::jsonb,
  selected_final_data jsonb not null,
  created_by uuid,
  device_id uuid,
  created_at timestamptz not null default now()
);

revoke all on table private.product_merge_backups from public, anon, authenticated, service_role;
create index if not exists product_merge_backups_survivor_idx on private.product_merge_backups (survivor_product_id, created_at desc);
create index if not exists product_merge_backups_absorbed_idx on private.product_merge_backups (absorbed_product_id, created_at desc);

create or replace function private.clear_stock_reconfirmation_after_new_conference()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare v_conference_created_at timestamptz;
begin
  select c.created_at into v_conference_created_at from public.conferences c where c.id = new.conference_id;
  if v_conference_created_at is null then return new; end if;
  if exists (
    select 1 from public.products p
    where p.id = new.product_id
      and p.stock_reconfirmation_required_at is not null
      and v_conference_created_at >= p.stock_reconfirmation_required_at
  ) then
    perform set_config('app.suppress_row_audit', 'on', true);
    update public.products
    set stock_reconfirmation_required_at = null
    where id = new.product_id
      and stock_reconfirmation_required_at is not null
      and v_conference_created_at >= stock_reconfirmation_required_at;
    perform set_config('app.suppress_row_audit', 'off', true);
  end if;
  return new;
exception when others then
  perform set_config('app.suppress_row_audit', 'off', true);
  raise;
end;
$$;

revoke all on function private.clear_stock_reconfirmation_after_new_conference() from public, anon, authenticated, service_role;
drop trigger if exists conference_items_clear_stock_reconfirmation on public.conference_items;
create trigger conference_items_clear_stock_reconfirmation
after insert or update of product_id on public.conference_items
for each row execute function private.clear_stock_reconfirmation_after_new_conference();

create or replace function private.merge_products_impl(
  p_product_a_id uuid,
  p_product_b_id uuid,
  p_final_name text,
  p_final_category_id uuid,
  p_final_unit text,
  p_survivor_equivalent_quantity numeric,
  p_absorbed_equivalent_quantity numeric,
  p_initial_price_source text,
  p_device_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_product_a public.products%rowtype;
  v_product_b public.products%rowtype;
  v_survivor public.products%rowtype;
  v_absorbed public.products%rowtype;
  v_survivor_id uuid;
  v_absorbed_id uuid;
  v_final_name text;
  v_final_unit text;
  v_price_source text;
  v_survivor_factor numeric := 1;
  v_absorbed_factor numeric := 1;
  v_final_sort_order integer;
  v_final_initial_price numeric;
  v_final_initial_price_at timestamptz;
  v_final_initial_stock numeric;
  v_merge_at timestamptz := clock_timestamp();
  v_backup_id uuid;
  v_entry_items_before jsonb;
  v_conference_items_before jsonb;
  v_scope_conferences_before jsonb;
  v_entry_count integer := 0;
  v_conference_item_count integer := 0;
  v_scope_conference_count integer := 0;
  v_overlap_conference_count integer := 0;
  v_survivor_after jsonb;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;
  if p_product_a_id is null or p_product_b_id is null or p_product_a_id = p_product_b_id then raise exception 'Escolha dois produtos diferentes para mesclar.'; end if;

  perform 1 from public.products p where p.id in (p_product_a_id, p_product_b_id) order by p.id for update;

  select * into v_product_a from public.products p where p.id = p_product_a_id and p.deleted_at is null;
  if not found then raise exception 'O primeiro produto não está ativo.'; end if;
  select * into v_product_b from public.products p where p.id = p_product_b_id and p.deleted_at is null;
  if not found then raise exception 'O segundo produto não está ativo.'; end if;

  if v_product_a.category_id is null or v_product_b.category_id is null then raise exception 'Produtos com cadastro pendente não podem ser mesclados.'; end if;

  if (v_product_a.created_at, v_product_a.id::text) <= (v_product_b.created_at, v_product_b.id::text) then
    v_survivor := v_product_a; v_absorbed := v_product_b;
  else
    v_survivor := v_product_b; v_absorbed := v_product_a;
  end if;
  v_survivor_id := v_survivor.id;
  v_absorbed_id := v_absorbed.id;

  v_final_name := regexp_replace(btrim(coalesce(p_final_name, '')), '[[:space:]]+', ' ', 'g');
  if char_length(v_final_name) < 1 or char_length(v_final_name) > 200 then raise exception 'Nome final do produto inválido.'; end if;

  if p_final_category_id is null or not exists (
    select 1 from public.categories c where c.id = p_final_category_id and c.deleted_at is null
  ) then raise exception 'Categoria final inválida ou excluída.'; end if;

  if exists (
    select 1 from public.products p
    where p.deleted_at is null
      and p.id not in (v_survivor_id, v_absorbed_id)
      and private.normalize_name(p.name) = private.normalize_name(v_final_name)
  ) then raise exception 'Já existe outro produto ativo com esse nome.'; end if;

  v_final_unit := upper(btrim(coalesce(p_final_unit, '')));
  if v_final_unit not in ('UN','KG','SC','CX','PCT','FD','BL','GL','PET','ROLO','LATA','BARRA','PT') then raise exception 'Unidade final inválida.'; end if;
  if v_final_unit not in (v_survivor.unit, v_absorbed.unit) then raise exception 'A unidade final deve ser uma das unidades atuais dos produtos.'; end if;

  if v_survivor.unit <> v_absorbed.unit then
    if p_survivor_equivalent_quantity is null or p_survivor_equivalent_quantity <= 0
      or p_absorbed_equivalent_quantity is null or p_absorbed_equivalent_quantity <= 0
    then raise exception 'Informe a equivalência entre as duas unidades.'; end if;
    if v_final_unit = v_survivor.unit then
      v_survivor_factor := 1;
      v_absorbed_factor := p_survivor_equivalent_quantity / p_absorbed_equivalent_quantity;
    else
      v_survivor_factor := p_absorbed_equivalent_quantity / p_survivor_equivalent_quantity;
      v_absorbed_factor := 1;
    end if;
  end if;

  v_price_source := lower(btrim(coalesce(p_initial_price_source, 'none')));
  if v_price_source not in ('survivor','absorbed','none') then raise exception 'Referência inicial de preço inválida.'; end if;

  if v_price_source = 'survivor' then
    if v_survivor.initial_price is null then raise exception 'O produto mais antigo não possui preço inicial para manter.'; end if;
    v_final_initial_price := v_survivor.initial_price / v_survivor_factor;
    v_final_initial_price_at := v_survivor.initial_price_at;
  elsif v_price_source = 'absorbed' then
    if v_absorbed.initial_price is null then raise exception 'O produto absorvido não possui preço inicial para manter.'; end if;
    v_final_initial_price := v_absorbed.initial_price / v_absorbed_factor;
    v_final_initial_price_at := v_absorbed.initial_price_at;
  else
    v_final_initial_price := null;
    v_final_initial_price_at := null;
  end if;

  v_final_initial_stock := case when v_survivor.initial_stock_quantity is null then null else v_survivor.initial_stock_quantity * v_survivor_factor end;

  lock table public.entry_items in share row exclusive mode;
  lock table public.conference_items in share row exclusive mode;
  lock table public.conferences in share row exclusive mode;

  select coalesce(jsonb_agg(to_jsonb(ei) order by ei.entry_id, ei.position, ei.id), '[]'::jsonb), count(*)::integer
    into v_entry_items_before, v_entry_count from public.entry_items ei where ei.product_id in (v_survivor_id, v_absorbed_id);
  select coalesce(jsonb_agg(to_jsonb(ci) order by ci.conference_id, ci.position, ci.id), '[]'::jsonb), count(*)::integer
    into v_conference_items_before, v_conference_item_count from public.conference_items ci where ci.product_id in (v_survivor_id, v_absorbed_id);
  select coalesce(jsonb_agg(to_jsonb(c) order by c.created_at, c.id), '[]'::jsonb), count(*)::integer
    into v_scope_conferences_before, v_scope_conference_count from public.conferences c where c.scope_product_id in (v_survivor_id, v_absorbed_id);
  select count(*)::integer into v_overlap_conference_count
    from public.conference_items s join public.conference_items a on a.conference_id = s.conference_id
    where s.product_id = v_survivor_id and a.product_id = v_absorbed_id;

  insert into private.product_merge_backups (
    survivor_product_id, absorbed_product_id, survivor_before, absorbed_before,
    entry_items_before, conference_items_before, scope_conferences_before,
    selected_final_data, created_by, device_id
  ) values (
    v_survivor_id, v_absorbed_id, to_jsonb(v_survivor), to_jsonb(v_absorbed),
    v_entry_items_before, v_conference_items_before, v_scope_conferences_before,
    jsonb_build_object(
      'name', v_final_name, 'category_id', p_final_category_id, 'unit', v_final_unit,
      'survivor_equivalent_quantity', p_survivor_equivalent_quantity,
      'absorbed_equivalent_quantity', p_absorbed_equivalent_quantity,
      'initial_price_source', v_price_source
    ),
    v_uid, p_device_id
  ) returning id into v_backup_id;

  perform set_config('app.device_id', p_device_id::text, true);
  perform set_config('app.suppress_row_audit', 'on', true);

  begin
    if v_survivor_factor <> 1 then
      update public.entry_items set quantity = quantity * v_survivor_factor,
        unit_price = case when unit_price is null then null else unit_price / v_survivor_factor end
      where product_id = v_survivor_id;
      update public.conference_items set quantity = quantity * v_survivor_factor where product_id = v_survivor_id;
    end if;

    if v_absorbed_factor <> 1 then
      update public.entry_items set quantity = quantity * v_absorbed_factor,
        unit_price = case when unit_price is null then null else unit_price / v_absorbed_factor end
      where product_id = v_absorbed_id;
      update public.conference_items set quantity = quantity * v_absorbed_factor where product_id = v_absorbed_id;
    end if;

    update public.conference_items s
    set quantity = s.quantity + a.quantity
    from public.conference_items a
    where s.conference_id = a.conference_id and s.product_id = v_survivor_id and a.product_id = v_absorbed_id;

    delete from public.conference_items a
    where a.product_id = v_absorbed_id
      and exists (select 1 from public.conference_items s where s.conference_id = a.conference_id and s.product_id = v_survivor_id);

    update public.conference_items set product_id = v_survivor_id where product_id = v_absorbed_id;
    update public.entry_items set product_id = v_survivor_id where product_id = v_absorbed_id;
    update public.conferences set scope_product_id = v_survivor_id where scope_product_id = v_absorbed_id;

    delete from public.products where id = v_absorbed_id;

    if p_final_category_id = v_survivor.category_id then
      v_final_sort_order := v_survivor.sort_order;
    else
      select coalesce(max(p.sort_order),0)+1 into v_final_sort_order
      from public.products p
      where p.category_id = p_final_category_id and p.deleted_at is null and p.id <> v_survivor_id;
    end if;

    update public.products
    set name=v_final_name, category_id=p_final_category_id, sort_order=v_final_sort_order,
      unit=v_final_unit, initial_stock_quantity=v_final_initial_stock,
      initial_stock_at=v_survivor.initial_stock_at, initial_price=v_final_initial_price,
      initial_price_at=v_final_initial_price_at, stock_reconfirmation_required_at=v_merge_at
    where id=v_survivor_id;

    perform set_config('app.suppress_row_audit', 'off', true);
  exception when others then
    perform set_config('app.suppress_row_audit', 'off', true);
    raise;
  end;

  select to_jsonb(p) into v_survivor_after from public.products p where p.id=v_survivor_id;

  insert into public.audit_log (actor_user_id,device_id,action,entity_type,entity_id,before_data,after_data,metadata)
  values (
    v_uid,p_device_id,'PRODUCT_MERGE','products',v_survivor_id,
    jsonb_build_object('survivor',to_jsonb(v_survivor),'absorbed',to_jsonb(v_absorbed),'entry_items_count',v_entry_count,'conference_items_count',v_conference_item_count,'scope_conferences_count',v_scope_conference_count),
    jsonb_build_object('survivor',v_survivor_after,'stock_requires_conference',true),
    jsonb_build_object('backup_id',v_backup_id,'absorbed_product_id',v_absorbed_id,'survivor_factor',v_survivor_factor,'absorbed_factor',v_absorbed_factor,'overlap_conference_count',v_overlap_conference_count,'initial_price_source',v_price_source)
  );

  return jsonb_build_object(
    'backup_id',v_backup_id,'survivor_product_id',v_survivor_id,'absorbed_product_id',v_absorbed_id,
    'entry_items_count',v_entry_count,'conference_items_count',v_conference_item_count,
    'scope_conferences_count',v_scope_conference_count,'overlap_conference_count',v_overlap_conference_count,
    'stock_requires_conference',true
  );
end;
$$;

revoke all on function private.merge_products_impl(uuid,uuid,text,uuid,text,numeric,numeric,text,uuid) from public,anon,authenticated,service_role;
grant execute on function private.merge_products_impl(uuid,uuid,text,uuid,text,numeric,numeric,text,uuid) to authenticated,service_role;

create or replace function public.merge_products(
  p_product_a_id uuid,p_product_b_id uuid,p_final_name text,p_final_category_id uuid,p_final_unit text,
  p_survivor_equivalent_quantity numeric,p_absorbed_equivalent_quantity numeric,p_initial_price_source text,p_device_id uuid
)
returns jsonb
language sql
security invoker
set search_path=pg_catalog,private
as $$
  select private.merge_products_impl(
    p_product_a_id,p_product_b_id,p_final_name,p_final_category_id,p_final_unit,
    p_survivor_equivalent_quantity,p_absorbed_equivalent_quantity,p_initial_price_source,p_device_id
  );
$$;

revoke all on function public.merge_products(uuid,uuid,text,uuid,text,numeric,numeric,text,uuid) from public,anon,authenticated,service_role;
grant execute on function public.merge_products(uuid,uuid,text,uuid,text,numeric,numeric,text,uuid) to authenticated,service_role;

commit;
