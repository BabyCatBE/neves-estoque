create or replace function private.create_entry_impl(
  p_supplier_id uuid,
  p_effective_at timestamptz,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_items jsonb,
  p_observation text default null
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_entry_id uuid;
  v_existing_user uuid;
  v_item jsonb;
  v_position integer := 0;
  v_product_id uuid;
  v_quantity numeric;
  v_unit_price numeric;
  v_new_product jsonb;
  v_client_id text;
  v_product_name text;
  v_product_unit text;
  v_category_id uuid;
  v_next_order integer;
  v_created_products jsonb := '{}'::jsonb;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if not private.is_device_allowed(p_device_id) then raise exception 'Dispositivo não autorizado.'; end if;
  if p_supplier_id is null or not exists (select 1 from public.suppliers s where s.id=p_supplier_id and s.deleted_at is null) then raise exception 'Fornecedor inválido ou excluído.'; end if;
  if p_effective_at is null then raise exception 'Data/hora efetiva da Entrada é obrigatória.'; end if;
  if p_idempotency_key is null then raise exception 'Chave de idempotência é obrigatória.'; end if;
  if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)=0 then raise exception 'A Entrada precisa de pelo menos um item.'; end if;

  select e.id,e.registered_by into v_entry_id,v_existing_user from public.entries e where e.idempotency_key=p_idempotency_key limit 1;
  if v_entry_id is not null then
    if v_existing_user<>v_uid then raise exception 'Chave de idempotência já utilizada.'; end if;
    return v_entry_id;
  end if;

  perform set_config('app.device_id',p_device_id::text,true);
  insert into public.entries(supplier_id,effective_at,observation,registered_by,device_id,idempotency_key)
  values(p_supplier_id,p_effective_at,nullif(btrim(coalesce(p_observation,'')),''),v_uid,p_device_id,p_idempotency_key)
  returning id into v_entry_id;

  for v_item in select value from jsonb_array_elements(p_items) loop
    v_position:=v_position+1; v_product_id:=null; v_new_product:=null; v_client_id:=null;
    v_product_name:=null; v_product_unit:=null; v_category_id:=null; v_next_order:=null;
    begin
      if v_item?'product_id' and v_item->>'product_id' is not null and btrim(v_item->>'product_id')<>'' then v_product_id:=(v_item->>'product_id')::uuid; end if;
      if v_item?'new_product' then v_new_product:=v_item->'new_product'; end if;
      v_quantity:=nullif(v_item->>'quantity','')::numeric;
      if v_item?'unit_price' and v_item->>'unit_price' is not null and btrim(v_item->>'unit_price')<>'' then v_unit_price:=(v_item->>'unit_price')::numeric; else v_unit_price:=null; end if;
    exception when others then raise exception 'Item % possui formato inválido.',v_position; end;

    if v_quantity is null or v_quantity<=0 then raise exception 'Quantidade deve ser maior que zero no item %.',v_position; end if;
    if v_unit_price is not null and v_unit_price<0 then raise exception 'Preço não pode ser negativo no item %.',v_position; end if;
    if v_product_id is not null and v_new_product is not null then raise exception 'Item % não pode apontar para Produto existente e Produto novo ao mesmo tempo.',v_position; end if;

    if v_product_id is null then
      if v_new_product is null or jsonb_typeof(v_new_product)<>'object' then raise exception 'Produto é obrigatório no item %.',v_position; end if;
      v_client_id:=nullif(btrim(coalesce(v_new_product->>'client_id','')),'');
      if v_client_id is null or char_length(v_client_id)>100 then raise exception 'Identificador do Produto novo é inválido no item %.',v_position; end if;
      if v_created_products?v_client_id then
        v_product_id:=(v_created_products->>v_client_id)::uuid;
      else
        v_product_name:=regexp_replace(btrim(coalesce(v_new_product->>'name','')),'[[:space:]]+',' ','g');
        if char_length(v_product_name)<1 or char_length(v_product_name)>200 then raise exception 'Nome do Produto novo é inválido no item %.',v_position; end if;
        v_product_unit:=upper(btrim(coalesce(v_new_product->>'unit','')));
        if v_product_unit not in ('UN','KG','SC','CX','PCT','FD','BL','GL','PET','ROLO','LATA','BARRA','PT') then raise exception 'Unidade do Produto novo é inválida no item %.',v_position; end if;
        if v_new_product?'category_id' and v_new_product->>'category_id' is not null and btrim(v_new_product->>'category_id')<>'' then
          begin v_category_id:=(v_new_product->>'category_id')::uuid; exception when others then raise exception 'Categoria do Produto novo é inválida no item %.',v_position; end;
        end if;
        if v_category_id is not null then
          perform 1 from public.categories c where c.id=v_category_id and c.deleted_at is null for update;
          if not found then raise exception 'Categoria do Produto novo é inválida ou excluída no item %.',v_position; end if;
          select coalesce(max(p.sort_order),0)+1 into v_next_order from public.products p where p.category_id=v_category_id and p.deleted_at is null;
        end if;
        begin
          insert into public.products(name,category_id,unit,sort_order,quick_entry_idempotency_key)
          values(v_product_name,v_category_id,v_product_unit,v_next_order,case when v_category_id is null then p_idempotency_key else null end)
          returning id into v_product_id;
        exception when unique_violation then raise exception 'Já existe um Produto ativo com o nome “%”. Pesquise e selecione o cadastro existente.',v_product_name; end;
        v_created_products:=v_created_products||jsonb_build_object(v_client_id,v_product_id::text);
      end if;
    else
      if not exists(select 1 from public.products p where p.id=v_product_id and p.deleted_at is null and (p.category_id is not null or p.quick_entry_idempotency_key=p_idempotency_key))
      then raise exception 'Produto inválido, excluído ou pendente de categoria no item %.',v_position; end if;
    end if;

    insert into public.entry_items(entry_id,product_id,quantity,unit_price,position)
    values(v_entry_id,v_product_id,v_quantity,v_unit_price,v_position);
  end loop;
  return v_entry_id;
end;
$$;

create or replace function private.update_product_details_impl(p_product_id uuid,p_name text,p_category_id uuid)
returns void language plpgsql security definer set search_path=pg_catalog,public,private,auth as $$
declare v_name text; v_old_category_id uuid; v_old_sort_order integer; v_next_order integer;
begin
  if not private.is_authorized_user() then raise exception 'Acesso não autorizado.'; end if;
  if p_product_id is null then raise exception 'Produto inválido.'; end if;
  v_name:=regexp_replace(btrim(coalesce(p_name,'')),'[[:space:]]+',' ','g');
  if char_length(v_name)<1 or char_length(v_name)>200 then raise exception 'Nome do produto inválido.'; end if;
  if p_category_id is null then raise exception 'Categoria é obrigatória.'; end if;
  select p.category_id,p.sort_order into v_old_category_id,v_old_sort_order from public.products p where p.id=p_product_id and p.deleted_at is null for update;
  if not found then raise exception 'Produto não encontrado.'; end if;
  perform 1 from public.categories c where c.id=p_category_id and c.deleted_at is null for update;
  if not found then raise exception 'Categoria inválida ou excluída.'; end if;
  if v_old_category_id=p_category_id then update public.products set name=v_name,quick_entry_idempotency_key=null where id=p_product_id; return; end if;
  select coalesce(max(p.sort_order),0)+1 into v_next_order from public.products p where p.category_id=p_category_id and p.deleted_at is null;
  update public.products set name=v_name,category_id=p_category_id,sort_order=v_next_order,quick_entry_idempotency_key=null where id=p_product_id;
end;
$$;

revoke execute on function public.create_quick_entry_product(text,text,uuid,uuid) from authenticated;
revoke execute on function private.create_quick_entry_product_impl(text,text,uuid,uuid) from authenticated;
