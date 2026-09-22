-- Neves Estoque
-- Migration inicial do núcleo de dados e segurança.
-- Fonte oficial versionada no Git. Aplicar no único projeto Supabase Neves Estoque.

begin;

-- ---------------------------------------------------------------------------
-- Superfície e privilégios padrão
-- ---------------------------------------------------------------------------

create schema if not exists private;

revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

alter default privileges for role postgres in schema public
  revoke select, insert, update, delete on tables from anon, authenticated;

alter default privileges for role postgres in schema public
  revoke execute on functions from public, anon, authenticated;

alter default privileges for role postgres in schema public
  revoke usage, select on sequences from anon, authenticated;

alter default privileges for role postgres in schema private
  revoke execute on functions from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Helpers internos
-- ---------------------------------------------------------------------------

create or replace function private.normalize_name(value text)
returns text
language sql
immutable
strict
set search_path = pg_catalog
as $$
  select lower(regexp_replace(btrim(value), '[[:space:]]+', ' ', 'g'));
$$;

revoke all on function private.normalize_name(text) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Tabelas
-- ---------------------------------------------------------------------------

create table public.app_users (
  id uuid primary key default gen_random_uuid(),
  auth_user_id uuid unique references auth.users(id) on delete set null,
  email text not null,
  display_name text,
  role_name text not null default 'admin',
  is_authorized boolean not null default true,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null,
  constraint app_users_email_length check (char_length(btrim(email)) between 3 and 320),
  constraint app_users_display_name_length check (
    display_name is null or char_length(btrim(display_name)) between 1 and 160
  ),
  constraint app_users_role_length check (char_length(btrim(role_name)) between 1 and 80)
);

create unique index app_users_email_normalized_uq
  on public.app_users (lower(btrim(email)));

create index app_users_auth_user_id_idx
  on public.app_users (auth_user_id);

create table public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  device_key uuid not null unique,
  friendly_name text not null,
  is_allowed boolean not null default true,
  registered_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null,
  constraint devices_friendly_name_length check (
    char_length(btrim(friendly_name)) between 1 and 120
  )
);

create index devices_user_id_idx on public.devices (user_id);
create index devices_allowed_idx on public.devices (user_id, is_allowed);

create table public.categories (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  sort_order integer,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null,
  deleted_at timestamptz,
  deleted_by uuid references auth.users(id) on delete set null,
  restore_until timestamptz,
  constraint categories_name_length check (char_length(btrim(name)) between 1 and 120)
);

create unique index categories_active_name_uq
  on public.categories (private.normalize_name(name))
  where deleted_at is null;

create table public.products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  category_id uuid references public.categories(id) on delete restrict,
  unit text,
  initial_stock_quantity numeric(14,3),
  initial_stock_at timestamptz,
  initial_price numeric(14,2),
  initial_price_at timestamptz,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null,
  deleted_at timestamptz,
  deleted_by uuid references auth.users(id) on delete set null,
  restore_until timestamptz,
  constraint products_name_length check (char_length(btrim(name)) between 1 and 200),
  constraint products_unit_length check (
    unit is null or char_length(btrim(unit)) between 1 and 80
  ),
  constraint products_initial_stock_nonnegative check (
    initial_stock_quantity is null or initial_stock_quantity >= 0
  ),
  constraint products_initial_price_nonnegative check (
    initial_price is null or initial_price >= 0
  ),
  constraint products_initial_stock_pair check (
    (initial_stock_quantity is null) = (initial_stock_at is null)
  ),
  constraint products_initial_price_pair check (
    (initial_price is null) = (initial_price_at is null)
  )
);

create unique index products_active_name_uq
  on public.products (private.normalize_name(name))
  where deleted_at is null;

create index products_category_id_idx on public.products (category_id);

create table public.suppliers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null,
  deleted_at timestamptz,
  deleted_by uuid references auth.users(id) on delete set null,
  restore_until timestamptz,
  constraint suppliers_name_length check (char_length(btrim(name)) between 1 and 200)
);

create unique index suppliers_active_name_uq
  on public.suppliers (private.normalize_name(name))
  where deleted_at is null;

create table public.entries (
  id uuid primary key default gen_random_uuid(),
  supplier_id uuid not null references public.suppliers(id) on delete restrict,
  effective_at timestamptz not null,
  observation text,
  registered_by uuid not null references auth.users(id) on delete restrict,
  device_id uuid not null references public.devices(id) on delete restrict,
  idempotency_key uuid not null unique,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null,
  deleted_at timestamptz,
  deleted_by uuid references auth.users(id) on delete set null,
  restore_until timestamptz,
  constraint entries_observation_length check (
    observation is null or char_length(observation) <= 2000
  )
);

create index entries_supplier_effective_idx
  on public.entries (supplier_id, effective_at desc)
  where deleted_at is null;

create index entries_effective_idx
  on public.entries (effective_at desc)
  where deleted_at is null;

create index entries_registered_by_idx
  on public.entries (registered_by, created_at desc);

create table public.entry_items (
  id uuid primary key default gen_random_uuid(),
  entry_id uuid not null references public.entries(id) on delete restrict,
  product_id uuid not null references public.products(id) on delete restrict,
  quantity numeric(14,3) not null,
  unit_price numeric(14,2),
  position integer not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint entry_items_quantity_positive check (quantity > 0),
  constraint entry_items_price_nonnegative check (
    unit_price is null or unit_price >= 0
  ),
  constraint entry_items_position_positive check (position > 0),
  constraint entry_items_entry_position_uq unique (entry_id, position)
);

create index entry_items_product_idx
  on public.entry_items (product_id, entry_id);

create table public.conferences (
  id uuid primary key default gen_random_uuid(),
  effective_at timestamptz not null,
  physical_responsible text not null,
  observation text,
  registered_by uuid not null references auth.users(id) on delete restrict,
  device_id uuid not null references public.devices(id) on delete restrict,
  idempotency_key uuid not null unique,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null,
  deleted_at timestamptz,
  deleted_by uuid references auth.users(id) on delete set null,
  restore_until timestamptz,
  constraint conferences_responsible_length check (
    char_length(btrim(physical_responsible)) between 1 and 160
  ),
  constraint conferences_observation_length check (
    observation is null or char_length(observation) <= 2000
  )
);

create index conferences_effective_idx
  on public.conferences (effective_at desc)
  where deleted_at is null;

create index conferences_registered_by_idx
  on public.conferences (registered_by, created_at desc);

create table public.conference_items (
  id uuid primary key default gen_random_uuid(),
  conference_id uuid not null references public.conferences(id) on delete restrict,
  product_id uuid not null references public.products(id) on delete restrict,
  quantity numeric(14,3) not null,
  position integer not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint conference_items_quantity_nonnegative check (quantity >= 0),
  constraint conference_items_position_positive check (position > 0),
  constraint conference_items_conference_position_uq unique (conference_id, position),
  constraint conference_items_product_once_uq unique (conference_id, product_id)
);

create index conference_items_product_idx
  on public.conference_items (product_id, conference_id);

create table public.audit_log (
  id bigint generated always as identity primary key,
  actor_user_id uuid references auth.users(id) on delete set null,
  device_id uuid references public.devices(id) on delete set null,
  action text not null,
  entity_type text not null,
  entity_id uuid,
  before_data jsonb,
  after_data jsonb,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint audit_log_action_length check (char_length(action) between 1 and 80),
  constraint audit_log_entity_type_length check (char_length(entity_type) between 1 and 120)
);

create index audit_log_entity_idx
  on public.audit_log (entity_type, entity_id, created_at desc);

create index audit_log_actor_idx
  on public.audit_log (actor_user_id, created_at desc);

-- ---------------------------------------------------------------------------
-- Autorização e contexto confiável
-- ---------------------------------------------------------------------------

create or replace function private.is_google_user()
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, auth
as $$
  select
    auth.uid() is not null
    and coalesce(auth.jwt() -> 'app_metadata' ->> 'provider', '') = 'google';
$$;

create or replace function private.is_authorized_user()
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public, auth
as $$
  select
    private.is_google_user()
    and exists (
      select 1
      from public.app_users au
      where au.is_authorized
        and (
          au.auth_user_id = auth.uid()
          or (
            au.auth_user_id is null
            and lower(btrim(au.email)) = lower(btrim(coalesce(auth.jwt() ->> 'email', '')))
          )
        )
    );
$$;

create or replace function private.is_device_allowed(p_device_id uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public, auth
as $$
  select
    private.is_authorized_user()
    and exists (
      select 1
      from public.devices d
      where d.id = p_device_id
        and d.user_id = auth.uid()
        and d.is_allowed
    );
$$;

create or replace function private.request_device_id()
returns uuid
language plpgsql
stable
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_headers jsonb;
  v_raw text;
  v_device uuid;
begin
  begin
    v_headers := current_setting('request.headers', true)::jsonb;
    v_raw := v_headers ->> 'x-device-id';
    if v_raw is null or btrim(v_raw) = '' then
      return null;
    end if;
    v_device := v_raw::uuid;
  exception
    when others then
      return null;
  end;

  if private.is_device_allowed(v_device) then
    return v_device;
  end if;

  return null;
end;
$$;

revoke all on function private.is_google_user() from public, anon;
revoke all on function private.is_authorized_user() from public, anon;
revoke all on function private.is_device_allowed(uuid) from public, anon;
revoke all on function private.request_device_id() from public, anon;

grant execute on function private.is_google_user() to authenticated;
grant execute on function private.is_authorized_user() to authenticated;
grant execute on function private.is_device_allowed(uuid) to authenticated;
grant execute on function private.request_device_id() to authenticated;

-- ---------------------------------------------------------------------------
-- Metadados, lixeira e auditoria
-- ---------------------------------------------------------------------------

create or replace function private.set_common_metadata()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, auth
as $$
begin
  if tg_op = 'INSERT' then
    new.created_at := coalesce(new.created_at, now());
    new.updated_at := now();

    if auth.uid() is not null then
      new.created_by := auth.uid();
      new.updated_by := auth.uid();
    end if;
  elsif tg_op = 'UPDATE' then
    new.created_at := old.created_at;
    new.created_by := old.created_by;
    new.updated_at := now();

    if auth.uid() is not null then
      new.updated_by := auth.uid();
    else
      new.updated_by := old.updated_by;
    end if;
  end if;

  return new;
end;
$$;

create or replace function private.manage_soft_delete()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, auth
as $$
begin
  if old.deleted_at is null and new.deleted_at is not null then
    new.deleted_at := now();
    new.deleted_by := auth.uid();
    new.restore_until := new.deleted_at + interval '7 days';
  elsif old.deleted_at is not null and new.deleted_at is null then
    if old.restore_until is null or now() > old.restore_until then
      raise exception 'Prazo de restauração expirado.';
    end if;

    new.deleted_by := null;
    new.restore_until := null;
  elsif old.deleted_at is not null and new.deleted_at is not null then
    new.deleted_at := old.deleted_at;
    new.deleted_by := old.deleted_by;
    new.restore_until := old.restore_until;
  else
    new.deleted_by := null;
    new.restore_until := null;
  end if;

  return new;
end;
$$;

create or replace function private.audit_row_change()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_before jsonb;
  v_after jsonb;
  v_id uuid;
  v_action text;
  v_device uuid;
  v_candidate text;
begin
  if tg_op = 'INSERT' then
    v_before := null;
    v_after := to_jsonb(new);
    v_action := 'CREATE';
  elsif tg_op = 'UPDATE' then
    v_before := to_jsonb(old);
    v_after := to_jsonb(new);

    if (v_before ->> 'deleted_at') is null and (v_after ->> 'deleted_at') is not null then
      v_action := 'SOFT_DELETE';
    elsif (v_before ->> 'deleted_at') is not null and (v_after ->> 'deleted_at') is null then
      v_action := 'RESTORE';
    else
      v_action := 'UPDATE';
    end if;
  else
    v_before := to_jsonb(old);
    v_after := null;
    v_action := 'DELETE';
  end if;

  begin
    v_id := coalesce(v_after ->> 'id', v_before ->> 'id')::uuid;
  exception
    when others then
      v_id := null;
  end;

  v_candidate := coalesce(v_after ->> 'device_id', v_before ->> 'device_id');

  begin
    if v_candidate is not null and btrim(v_candidate) <> '' then
      v_device := v_candidate::uuid;
      if not private.is_device_allowed(v_device) then
        v_device := null;
      end if;
    else
      v_device := private.request_device_id();
    end if;
  exception
    when others then
      v_device := private.request_device_id();
  end;

  insert into public.audit_log (
    actor_user_id,
    device_id,
    action,
    entity_type,
    entity_id,
    before_data,
    after_data
  )
  values (
    auth.uid(),
    v_device,
    v_action,
    tg_table_name,
    v_id,
    v_before,
    v_after
  );

  if tg_op = 'DELETE' then
    return old;
  end if;

  return new;
end;
$$;

revoke all on function private.set_common_metadata() from public, anon, authenticated;
revoke all on function private.manage_soft_delete() from public, anon, authenticated;
revoke all on function private.audit_row_change() from public, anon, authenticated;

-- metadados principais
create trigger app_users_set_metadata
before insert or update on public.app_users
for each row execute function private.set_common_metadata();

create trigger devices_set_metadata
before insert or update on public.devices
for each row execute function private.set_common_metadata();

create trigger categories_set_metadata
before insert or update on public.categories
for each row execute function private.set_common_metadata();

create trigger products_set_metadata
before insert or update on public.products
for each row execute function private.set_common_metadata();

create trigger suppliers_set_metadata
before insert or update on public.suppliers
for each row execute function private.set_common_metadata();

create trigger entries_set_metadata
before insert or update on public.entries
for each row execute function private.set_common_metadata();

create trigger conferences_set_metadata
before insert or update on public.conferences
for each row execute function private.set_common_metadata();

-- lixeira
create trigger categories_soft_delete
before update on public.categories
for each row execute function private.manage_soft_delete();

create trigger products_soft_delete
before update on public.products
for each row execute function private.manage_soft_delete();

create trigger suppliers_soft_delete
before update on public.suppliers
for each row execute function private.manage_soft_delete();

create trigger entries_soft_delete
before update on public.entries
for each row execute function private.manage_soft_delete();

create trigger conferences_soft_delete
before update on public.conferences
for each row execute function private.manage_soft_delete();

-- auditoria
create trigger app_users_audit
after insert or update or delete on public.app_users
for each row execute function private.audit_row_change();

create trigger devices_audit
after insert or update or delete on public.devices
for each row execute function private.audit_row_change();

create trigger categories_audit
after insert or update or delete on public.categories
for each row execute function private.audit_row_change();

create trigger products_audit
after insert or update or delete on public.products
for each row execute function private.audit_row_change();

create trigger suppliers_audit
after insert or update or delete on public.suppliers
for each row execute function private.audit_row_change();

create trigger entries_audit
after insert or update or delete on public.entries
for each row execute function private.audit_row_change();

create trigger entry_items_audit
after insert or update or delete on public.entry_items
for each row execute function private.audit_row_change();

create trigger conferences_audit
after insert or update or delete on public.conferences
for each row execute function private.audit_row_change();

create trigger conference_items_audit
after insert or update or delete on public.conference_items
for each row execute function private.audit_row_change();

-- timestamps dos itens
create or replace function private.touch_item_updated_at()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog
as $$
begin
  if tg_op = 'UPDATE' then
    new.created_at := old.created_at;
    new.updated_at := now();
  else
    new.created_at := coalesce(new.created_at, now());
    new.updated_at := now();
  end if;

  return new;
end;
$$;

revoke all on function private.touch_item_updated_at() from public, anon, authenticated;

create trigger entry_items_touch
before insert or update on public.entry_items
for each row execute function private.touch_item_updated_at();

create trigger conference_items_touch
before insert or update on public.conference_items
for each row execute function private.touch_item_updated_at();

-- ---------------------------------------------------------------------------
-- RPCs mínimas de acesso/dispositivo
-- ---------------------------------------------------------------------------

create or replace function public.claim_app_access()
returns table (
  app_user_id uuid,
  role_name text
)
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_email text := lower(btrim(coalesce(auth.jwt() ->> 'email', '')));
  v_provider text := coalesce(auth.jwt() -> 'app_metadata' ->> 'provider', '');
begin
  if v_uid is null or v_provider <> 'google' or v_email = '' then
    raise exception 'Acesso não autorizado.';
  end if;

  update public.app_users au
  set auth_user_id = v_uid
  where au.is_authorized
    and lower(btrim(au.email)) = v_email
    and (au.auth_user_id is null or au.auth_user_id = v_uid)
  returning au.id, au.role_name
  into app_user_id, role_name;

  if app_user_id is null then
    raise exception 'Acesso não autorizado.';
  end if;

  return next;
end;
$$;

create or replace function public.register_device(
  p_device_key uuid,
  p_friendly_name text
)
returns table (
  device_id uuid,
  is_allowed boolean
)
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_name text := btrim(coalesce(p_friendly_name, ''));
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if p_device_key is null then
    raise exception 'Identificador do dispositivo é obrigatório.';
  end if;

  if char_length(v_name) < 1 or char_length(v_name) > 120 then
    raise exception 'Nome do dispositivo inválido.';
  end if;

  insert into public.devices (
    user_id,
    device_key,
    friendly_name,
    is_allowed,
    last_seen_at
  )
  values (
    v_uid,
    p_device_key,
    v_name,
    true,
    now()
  )
  on conflict (device_key) do update
    set friendly_name = excluded.friendly_name,
        last_seen_at = now()
    where public.devices.user_id = v_uid
  returning public.devices.id, public.devices.is_allowed
  into device_id, is_allowed;

  if device_id is null then
    raise exception 'Dispositivo pertence a outro usuário.';
  end if;

  return next;
end;
$$;

revoke all on function public.claim_app_access() from public, anon, authenticated;
revoke all on function public.register_device(uuid, text) from public, anon, authenticated;

grant execute on function public.claim_app_access() to authenticated;
grant execute on function public.register_device(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- RLS e grants
-- ---------------------------------------------------------------------------

alter table public.app_users enable row level security;
alter table public.devices enable row level security;
alter table public.categories enable row level security;
alter table public.products enable row level security;
alter table public.suppliers enable row level security;
alter table public.entries enable row level security;
alter table public.entry_items enable row level security;
alter table public.conferences enable row level security;
alter table public.conference_items enable row level security;
alter table public.audit_log enable row level security;

revoke all on table public.app_users from anon, authenticated;
revoke all on table public.devices from anon, authenticated;
revoke all on table public.categories from anon, authenticated;
revoke all on table public.products from anon, authenticated;
revoke all on table public.suppliers from anon, authenticated;
revoke all on table public.entries from anon, authenticated;
revoke all on table public.entry_items from anon, authenticated;
revoke all on table public.conferences from anon, authenticated;
revoke all on table public.conference_items from anon, authenticated;
revoke all on table public.audit_log from anon, authenticated;

grant select on public.app_users to authenticated;
grant select on public.devices to authenticated;
grant select, insert, update on public.categories to authenticated;
grant select, insert, update on public.products to authenticated;
grant select, insert, update on public.suppliers to authenticated;
grant select on public.entries to authenticated;
grant select on public.entry_items to authenticated;
grant select on public.conferences to authenticated;
grant select on public.conference_items to authenticated;
grant select on public.audit_log to authenticated;

create policy app_users_select_self
on public.app_users
for select
to authenticated
using (
  auth.uid() is not null
  and private.is_google_user()
  and (
    auth_user_id = auth.uid()
    or (
      auth_user_id is null
      and lower(btrim(email)) = lower(btrim(coalesce(auth.jwt() ->> 'email', '')))
    )
  )
);

create policy devices_select_own
on public.devices
for select
to authenticated
using (
  private.is_authorized_user()
  and user_id = auth.uid()
);

create policy categories_select_authorized
on public.categories
for select
to authenticated
using ((select private.is_authorized_user()));

create policy categories_insert_authorized
on public.categories
for insert
to authenticated
with check ((select private.is_authorized_user()));

create policy categories_update_authorized
on public.categories
for update
to authenticated
using ((select private.is_authorized_user()))
with check ((select private.is_authorized_user()));

create policy products_select_authorized
on public.products
for select
to authenticated
using ((select private.is_authorized_user()));

create policy products_insert_authorized
on public.products
for insert
to authenticated
with check ((select private.is_authorized_user()));

create policy products_update_authorized
on public.products
for update
to authenticated
using ((select private.is_authorized_user()))
with check ((select private.is_authorized_user()));

create policy suppliers_select_authorized
on public.suppliers
for select
to authenticated
using ((select private.is_authorized_user()));

create policy suppliers_insert_authorized
on public.suppliers
for insert
to authenticated
with check ((select private.is_authorized_user()));

create policy suppliers_update_authorized
on public.suppliers
for update
to authenticated
using ((select private.is_authorized_user()))
with check ((select private.is_authorized_user()));

create policy entries_select_authorized
on public.entries
for select
to authenticated
using ((select private.is_authorized_user()));

create policy entry_items_select_authorized
on public.entry_items
for select
to authenticated
using ((select private.is_authorized_user()));

create policy conferences_select_authorized
on public.conferences
for select
to authenticated
using ((select private.is_authorized_user()));

create policy conference_items_select_authorized
on public.conference_items
for select
to authenticated
using ((select private.is_authorized_user()));

create policy audit_log_select_authorized
on public.audit_log
for select
to authenticated
using ((select private.is_authorized_user()));

-- ---------------------------------------------------------------------------
-- VIEW oficial do Estoque Atual
-- ---------------------------------------------------------------------------

create or replace view public.stock_current
with (security_invoker = true)
as
select
  p.id as product_id,
  p.name as product_name,
  p.category_id,
  p.unit,
  (
    coalesce(last_conf.quantity, p.initial_stock_quantity, 0::numeric)
    + coalesce(entries_after.quantity, 0::numeric)
  )::numeric(14,3) as current_quantity,
  last_conf.effective_at as last_conference_at,
  last_entry.effective_at as last_entry_at,
  last_entry.supplier_id as current_supplier_id,
  coalesce(last_price.unit_price, p.initial_price) as current_price,
  coalesce(last_price.effective_at, p.initial_price_at) as current_price_at,
  case
    when coalesce(last_price.unit_price, p.initial_price) is null then null
    else (
      (
        coalesce(last_conf.quantity, p.initial_stock_quantity, 0::numeric)
        + coalesce(entries_after.quantity, 0::numeric)
      )
      * coalesce(last_price.unit_price, p.initial_price)
    )::numeric(16,2)
  end as current_value
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
