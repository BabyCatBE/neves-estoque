begin;

alter table public.suppliers
  add column if not exists company text,
  add column if not exists phone text,
  add column if not exists observation text,
  add column if not exists purchase_frequency_days integer,
  add column if not exists preferred_order_weekday smallint,
  add column if not exists average_delivery_days integer,
  add column if not exists safety_margin_days integer;

alter table public.suppliers
  drop constraint if exists suppliers_company_length,
  add constraint suppliers_company_length
    check (company is null or char_length(btrim(company)) between 1 and 200),
  drop constraint if exists suppliers_phone_length,
  add constraint suppliers_phone_length
    check (phone is null or char_length(btrim(phone)) between 5 and 40),
  drop constraint if exists suppliers_observation_length,
  add constraint suppliers_observation_length
    check (observation is null or char_length(observation) <= 2000),
  drop constraint if exists suppliers_purchase_frequency_days_check,
  add constraint suppliers_purchase_frequency_days_check
    check (purchase_frequency_days is null or purchase_frequency_days between 1 and 3650),
  drop constraint if exists suppliers_preferred_order_weekday_check,
  add constraint suppliers_preferred_order_weekday_check
    check (preferred_order_weekday is null or preferred_order_weekday between 1 and 7),
  drop constraint if exists suppliers_average_delivery_days_check,
  add constraint suppliers_average_delivery_days_check
    check (average_delivery_days is null or average_delivery_days between 0 and 365),
  drop constraint if exists suppliers_safety_margin_days_check,
  add constraint suppliers_safety_margin_days_check
    check (safety_margin_days is null or safety_margin_days between 0 and 365);

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
  v_phone text := nullif(btrim(coalesce(p_phone, '')), '');
  v_observation text := nullif(btrim(coalesce(p_observation, '')), '');
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if char_length(v_name) < 1 or char_length(v_name) > 200 then
    raise exception 'Contato do fornecedor inválido.';
  end if;

  insert into public.suppliers (
    name,
    company,
    phone,
    observation,
    purchase_frequency_days,
    preferred_order_weekday,
    average_delivery_days,
    safety_margin_days
  )
  values (
    v_name,
    v_company,
    v_phone,
    v_observation,
    p_purchase_frequency_days,
    p_preferred_order_weekday,
    p_average_delivery_days,
    p_safety_margin_days
  )
  returning id into v_id;

  return v_id;
exception
  when unique_violation then
    raise exception 'Já existe um fornecedor ativo com este contato.';
end;
$$;

create or replace function public.create_supplier(
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
language sql
set search_path = pg_catalog, private
as $$
  select private.create_supplier_impl(
    p_name,
    p_company,
    p_phone,
    p_observation,
    p_purchase_frequency_days,
    p_preferred_order_weekday,
    p_average_delivery_days,
    p_safety_margin_days
  );
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
  v_phone text := nullif(btrim(coalesce(p_phone, '')), '');
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

create or replace function public.update_supplier(
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
language sql
set search_path = pg_catalog, private
as $$
  select private.update_supplier_impl(
    p_supplier_id,
    p_name,
    p_company,
    p_phone,
    p_observation,
    p_purchase_frequency_days,
    p_preferred_order_weekday,
    p_average_delivery_days,
    p_safety_margin_days
  );
$$;

create or replace function private.soft_delete_supplier_impl(p_supplier_id uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_count integer;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  update public.suppliers
  set deleted_at = now()
  where id = p_supplier_id
    and deleted_at is null;

  get diagnostics v_count = row_count;

  if v_count <> 1 then
    raise exception 'Fornecedor não encontrado.';
  end if;
end;
$$;

create or replace function public.soft_delete_supplier(p_supplier_id uuid)
returns void
language sql
set search_path = pg_catalog, private
as $$
  select private.soft_delete_supplier_impl(p_supplier_id);
$$;

create or replace function private.restore_supplier_impl(p_supplier_id uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_restore_until timestamptz;
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  select s.restore_until
    into v_restore_until
  from public.suppliers s
  where s.id = p_supplier_id
    and s.deleted_at is not null
  for update;

  if not found then
    raise exception 'Fornecedor não encontrado na lixeira.';
  end if;

  if v_restore_until is null or now() > v_restore_until then
    raise exception 'Prazo de restauração expirado.';
  end if;

  update public.suppliers
  set deleted_at = null
  where id = p_supplier_id;
exception
  when unique_violation then
    raise exception 'Já existe um fornecedor ativo com este contato.';
end;
$$;

create or replace function public.restore_supplier(p_supplier_id uuid)
returns void
language sql
set search_path = pg_catalog, private
as $$
  select private.restore_supplier_impl(p_supplier_id);
$$;

revoke all on function private.create_supplier_impl(text,text,text,text,integer,smallint,integer,integer)
from public, anon, authenticated;
revoke all on function private.update_supplier_impl(uuid,text,text,text,text,integer,smallint,integer,integer)
from public, anon, authenticated;
revoke all on function private.soft_delete_supplier_impl(uuid)
from public, anon, authenticated;
revoke all on function private.restore_supplier_impl(uuid)
from public, anon, authenticated;

grant execute on function private.create_supplier_impl(text,text,text,text,integer,smallint,integer,integer)
to authenticated;
grant execute on function private.update_supplier_impl(uuid,text,text,text,text,integer,smallint,integer,integer)
to authenticated;
grant execute on function private.soft_delete_supplier_impl(uuid)
to authenticated;
grant execute on function private.restore_supplier_impl(uuid)
to authenticated;

revoke all on function public.create_supplier(text,text,text,text,integer,smallint,integer,integer)
from public, anon, authenticated;
revoke all on function public.update_supplier(uuid,text,text,text,text,integer,smallint,integer,integer)
from public, anon, authenticated;
revoke all on function public.soft_delete_supplier(uuid)
from public, anon, authenticated;
revoke all on function public.restore_supplier(uuid)
from public, anon, authenticated;

grant execute on function public.create_supplier(text,text,text,text,integer,smallint,integer,integer)
to authenticated;
grant execute on function public.update_supplier(uuid,text,text,text,text,integer,smallint,integer,integer)
to authenticated;
grant execute on function public.soft_delete_supplier(uuid)
to authenticated;
grant execute on function public.restore_supplier(uuid)
to authenticated;

commit;
