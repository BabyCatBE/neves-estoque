create or replace function private.update_entry_impl(
  p_entry_id uuid,
  p_supplier_id uuid,
  p_effective_at timestamptz,
  p_device_id uuid,
  p_items jsonb,
  p_observation text default null
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_item jsonb;
  v_position integer := 0;
  v_product_id uuid;
  v_quantity numeric;
  v_unit_price numeric;
  v_before jsonb;
  v_after jsonb;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;
  if p_entry_id is null then raise exception 'Entrada inválida.'; end if;

  perform 1 from public.entries e where e.id=p_entry_id and e.deleted_at is null for update;
  if not found then raise exception 'Entrada não encontrada.'; end if;

  if p_supplier_id is null or not exists (
    select 1 from public.suppliers s where s.id=p_supplier_id and s.deleted_at is null
  ) then raise exception 'Fornecedor inválido ou excluído.'; end if;

  if p_effective_at is null then raise exception 'Data/hora efetiva da Entrada é obrigatória.'; end if;
  if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)=0 then
    raise exception 'A Entrada precisa de pelo menos um item.';
  end if;

  select jsonb_build_object(
    'entry',to_jsonb(e),
    'items',coalesce((select jsonb_agg(to_jsonb(ei) order by ei.position) from public.entry_items ei where ei.entry_id=e.id),'[]'::jsonb)
  ) into v_before from public.entries e where e.id=p_entry_id;

  perform set_config('app.device_id',p_device_id::text,true);
  perform set_config('app.suppress_row_audit','on',true);

  update public.entries
  set supplier_id=p_supplier_id,effective_at=p_effective_at,observation=nullif(btrim(coalesce(p_observation,'')),'')
  where id=p_entry_id;

  delete from public.entry_items where entry_id=p_entry_id;

  for v_item in select value from jsonb_array_elements(p_items) loop
    v_position:=v_position+1;
    begin
      v_product_id:=nullif(v_item->>'product_id','')::uuid;
      v_quantity:=nullif(v_item->>'quantity','')::numeric;
      if v_item ? 'unit_price' and v_item->>'unit_price' is not null and btrim(v_item->>'unit_price')<>'' then
        v_unit_price:=(v_item->>'unit_price')::numeric;
      else v_unit_price:=null;
      end if;
    exception when others then raise exception 'Item % possui formato inválido.',v_position;
    end;

    if v_product_id is null or not exists (
      select 1 from public.products p
      where p.id=v_product_id
        and (p.deleted_at is null or exists (
          select 1 from jsonb_array_elements(v_before->'items') old_item
          where old_item->>'product_id'=v_product_id::text
        ))
    ) then raise exception 'Produto inválido ou excluído no item %.',v_position; end if;

    if v_quantity is null or v_quantity<=0 then raise exception 'Quantidade deve ser maior que zero no item %.',v_position; end if;
    if v_unit_price is not null and v_unit_price<0 then raise exception 'Preço não pode ser negativo no item %.',v_position; end if;

    insert into public.entry_items(entry_id,product_id,quantity,unit_price,position)
    values(p_entry_id,v_product_id,v_quantity,v_unit_price,v_position);
  end loop;

  select jsonb_build_object(
    'entry',to_jsonb(e),
    'items',coalesce((select jsonb_agg(to_jsonb(ei) order by ei.position) from public.entry_items ei where ei.entry_id=e.id),'[]'::jsonb)
  ) into v_after from public.entries e where e.id=p_entry_id;

  insert into public.audit_log(actor_user_id,device_id,action,entity_type,entity_id,before_data,after_data)
  values(v_uid,p_device_id,'UPDATE','entries',p_entry_id,v_before,v_after);
end;
$$;

create or replace function public.update_entry(
  p_entry_id uuid,p_supplier_id uuid,p_effective_at timestamptz,p_device_id uuid,p_items jsonb,p_observation text default null
)
returns void
language sql
set search_path=pg_catalog,private
as $$
  select private.update_entry_impl(p_entry_id,p_supplier_id,p_effective_at,p_device_id,p_items,p_observation);
$$;

revoke all on function private.update_entry_impl(uuid,uuid,timestamptz,uuid,jsonb,text) from public,anon,authenticated;
grant execute on function private.update_entry_impl(uuid,uuid,timestamptz,uuid,jsonb,text) to authenticated;
revoke all on function public.update_entry(uuid,uuid,timestamptz,uuid,jsonb,text) from public,anon,authenticated;
grant execute on function public.update_entry(uuid,uuid,timestamptz,uuid,jsonb,text) to authenticated;
