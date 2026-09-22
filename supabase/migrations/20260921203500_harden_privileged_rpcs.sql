-- Hardening: SECURITY DEFINER fica fora do schema exposto.
-- As funções públicas RPC passam a ser wrappers SECURITY INVOKER.

begin;

-- ---------------------------------------------------------------------------
-- Helper estável para e-mail do JWT nas policies
-- ---------------------------------------------------------------------------

create or replace function private.current_user_email()
returns text
language sql
stable
security definer
set search_path = pg_catalog, auth
as $$
  select lower(btrim(coalesce(auth.jwt() ->> 'email', '')));
$$;

revoke all on function private.current_user_email() from public, anon;
grant execute on function private.current_user_email() to authenticated;

drop policy if exists app_users_select_self on public.app_users;

create policy app_users_select_self
on public.app_users
for select
to authenticated
using (
  (select private.is_google_user())
  and (
    auth_user_id = (select auth.uid())
    or (
      auth_user_id is null
      and lower(btrim(email)) = (select private.current_user_email())
    )
  )
);

-- ---------------------------------------------------------------------------
-- Move implementações privilegiadas para schema privado
-- ---------------------------------------------------------------------------

alter function public.claim_app_access()
  set schema private;
alter function private.claim_app_access()
  rename to claim_app_access_impl;

alter function public.register_device(uuid, text)
  set schema private;
alter function private.register_device(uuid, text)
  rename to register_device_impl;

alter function public.create_entry(uuid, timestamptz, uuid, uuid, jsonb, text)
  set schema private;
alter function private.create_entry(uuid, timestamptz, uuid, uuid, jsonb, text)
  rename to create_entry_impl;

alter function public.create_conference(timestamptz, text, uuid, uuid, jsonb, text)
  set schema private;
alter function private.create_conference(timestamptz, text, uuid, uuid, jsonb, text)
  rename to create_conference_impl;

revoke all on function private.claim_app_access_impl() from public, anon;
revoke all on function private.register_device_impl(uuid, text) from public, anon;
revoke all on function private.create_entry_impl(uuid, timestamptz, uuid, uuid, jsonb, text) from public, anon;
revoke all on function private.create_conference_impl(timestamptz, text, uuid, uuid, jsonb, text) from public, anon;

grant execute on function private.claim_app_access_impl() to authenticated;
grant execute on function private.register_device_impl(uuid, text) to authenticated;
grant execute on function private.create_entry_impl(uuid, timestamptz, uuid, uuid, jsonb, text) to authenticated;
grant execute on function private.create_conference_impl(timestamptz, text, uuid, uuid, jsonb, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Wrappers públicos sem privilégios elevados
-- ---------------------------------------------------------------------------

create or replace function public.claim_app_access()
returns table (
  app_user_id uuid,
  role_name text
)
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select * from private.claim_app_access_impl();
$$;

create or replace function public.register_device(
  p_device_key uuid,
  p_friendly_name text
)
returns table (
  device_id uuid,
  is_allowed boolean
)
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select *
  from private.register_device_impl(p_device_key, p_friendly_name);
$$;

create or replace function public.create_entry(
  p_supplier_id uuid,
  p_effective_at timestamptz,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_items jsonb,
  p_observation text default null
)
returns uuid
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.create_entry_impl(
    p_supplier_id,
    p_effective_at,
    p_device_id,
    p_idempotency_key,
    p_items,
    p_observation
  );
$$;

create or replace function public.create_conference(
  p_effective_at timestamptz,
  p_physical_responsible text,
  p_device_id uuid,
  p_idempotency_key uuid,
  p_items jsonb,
  p_observation text default null
)
returns uuid
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.create_conference_impl(
    p_effective_at,
    p_physical_responsible,
    p_device_id,
    p_idempotency_key,
    p_items,
    p_observation
  );
$$;

revoke all on function public.claim_app_access() from public, anon, authenticated;
revoke all on function public.register_device(uuid, text) from public, anon, authenticated;
revoke all on function public.create_entry(uuid, timestamptz, uuid, uuid, jsonb, text) from public, anon, authenticated;
revoke all on function public.create_conference(timestamptz, text, uuid, uuid, jsonb, text) from public, anon, authenticated;

grant execute on function public.claim_app_access() to authenticated;
grant execute on function public.register_device(uuid, text) to authenticated;
grant execute on function public.create_entry(uuid, timestamptz, uuid, uuid, jsonb, text) to authenticated;
grant execute on function public.create_conference(timestamptz, text, uuid, uuid, jsonb, text) to authenticated;

commit;
