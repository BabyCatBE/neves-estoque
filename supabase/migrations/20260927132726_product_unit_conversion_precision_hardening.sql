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
      initial_stock_quantity = case
        when initial_stock_quantity is null then null
        else initial_stock_quantity * p_new_quantity / p_old_quantity
      end,
      initial_price = case
        when initial_price is null then null
        else initial_price * p_old_quantity / p_new_quantity
      end
    where id = p_product_id;

    update public.entry_items
    set
      quantity = quantity * p_new_quantity / p_old_quantity,
      unit_price = case
        when unit_price is null then null
        else unit_price * p_old_quantity / p_new_quantity
      end
    where product_id = p_product_id;

    update public.conference_items
    set quantity = quantity * p_new_quantity / p_old_quantity
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
