create or replace function private.create_supplier_impl(
  p_name text,
  p_company text default null,
  p_phone text default null,
  p_observation text default null,
  p_purchase_frequency_days integer default null,
  p_preferred_order_weekday smallint default null,
  p_average_delivery_days integer default null,
  p_safety_margin_days integer default null
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_id uuid;
  v_name text := btrim(coalesce(p_name, ''));
  v_company text := nullif(btrim(coalesce(p_company, '')), '');
  v_phone_raw text := nullif(btrim(coalesce(p_phone, '')), '');
  v_phone text := null;
  v_observation text := nullif(btrim(coalesce(p_observation, '')), '');
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if char_length(v_name) < 1 or char_length(v_name) > 200 then
    raise exception 'Contato do fornecedor inválido.';
  end if;

  if v_phone_raw is not null then
    v_phone := regexp_replace(v_phone_raw, '[^0-9]', '', 'g');
    if char_length(v_phone) <> 11 then
      raise exception 'Telefone do fornecedor deve ter exatamente 11 dígitos.';
    end if;
  end if;

  insert into public.suppliers (
    name, company, phone, observation,
    purchase_frequency_days, preferred_order_weekday,
    average_delivery_days, safety_margin_days
  )
  values (
    v_name, v_company, v_phone, v_observation,
    p_purchase_frequency_days, p_preferred_order_weekday,
    p_average_delivery_days, p_safety_margin_days
  )
  returning id into v_id;

  return v_id;
exception
  when unique_violation then
    raise exception 'Já existe um fornecedor ativo com este contato.';
end;
$$;

create or replace function private.update_supplier_impl(
  p_supplier_id uuid,
  p_name text,
  p_company text,
  p_phone text,
  p_observation text default null,
  p_purchase_frequency_days integer default null,
  p_preferred_order_weekday smallint default null,
  p_average_delivery_days integer default null,
  p_safety_margin_days integer default null
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_name text := btrim(coalesce(p_name, ''));
  v_company text := nullif(btrim(coalesce(p_company, '')), '');
  v_phone_raw text := nullif(btrim(coalesce(p_phone, '')), '');
  v_phone text := null;
  v_observation text := nullif(btrim(coalesce(p_observation, '')), '');
  v_count integer;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_supplier_id is null then
    raise exception 'Fornecedor inválido.';
  end if;

  if char_length(v_name) < 1 or char_length(v_name) > 200 then
    raise exception 'Contato do fornecedor inválido.';
  end if;

  if v_phone_raw is not null then
    v_phone := regexp_replace(v_phone_raw, '[^0-9]', '', 'g');
    if char_length(v_phone) <> 11 then
      raise exception 'Telefone do fornecedor deve ter exatamente 11 dígitos.';
    end if;
  end if;

  update public.suppliers
  set
    name = v_name,
    company = v_company,
    phone = v_phone,
    observation = v_observation,
    purchase_frequency_days = p_purchase_frequency_days,
    preferred_order_weekday = p_preferred_order_weekday,
    average_delivery_days = p_average_delivery_days,
    safety_margin_days = p_safety_margin_days
  where id = p_supplier_id
    and deleted_at is null;

  get diagnostics v_count = row_count;

  if v_count <> 1 then
    raise exception 'Fornecedor não encontrado.';
  end if;
exception
  when unique_violation then
    raise exception 'Já existe um fornecedor ativo com este contato.';
end;
$$;
