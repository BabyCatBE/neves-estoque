begin;

alter table public.app_users
  add column if not exists username text,
  add column if not exists auth_method text not null default 'google';

alter table public.app_users
  drop constraint if exists app_users_username_format,
  add constraint app_users_username_format check (
    username is null
    or (
      username = lower(btrim(username))
      and char_length(username) between 3 and 32
      and username ~ '^[a-z0-9][a-z0-9._-]{2,31}$'
    )
  );

alter table public.app_users
  drop constraint if exists app_users_auth_method_check,
  add constraint app_users_auth_method_check check (
    auth_method in ('google', 'password')
  );

create unique index if not exists app_users_username_uq
  on public.app_users (username)
  where username is not null;

create or replace function private.is_authorized_user()
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public, auth
as $$
  select
    auth.uid() is not null
    and exists (
      select 1
      from public.app_users au
      where au.is_authorized
        and au.auth_user_id = auth.uid()
    );
$$;

revoke all on function private.is_authorized_user() from public, anon;
grant execute on function private.is_authorized_user() to authenticated;

create or replace function private.claim_app_access_impl()
returns table(app_user_id uuid, role_name text)
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_email text := lower(btrim(coalesce(auth.jwt() ->> 'email', '')));
  v_provider text := coalesce(auth.jwt() -> 'app_metadata' ->> 'provider', '');
begin
  if v_uid is null then
    raise exception 'Acesso não autorizado.';
  end if;

  select au.id, au.role_name
    into app_user_id, role_name
  from public.app_users au
  where au.is_authorized
    and au.auth_user_id = v_uid
  limit 1;

  if app_user_id is not null then
    return next;
    return;
  end if;

  if v_provider <> 'google' or v_email = '' then
    raise exception 'Acesso não autorizado.';
  end if;

  select au.id, au.role_name
    into app_user_id, role_name
  from public.app_users au
  where au.is_authorized
    and lower(btrim(au.email)) = v_email
    and au.auth_method = 'google'
    and au.auth_user_id is null
  for update;

  if app_user_id is null then
    raise exception 'Acesso não autorizado.';
  end if;

  update public.app_users au
  set auth_user_id = v_uid
  where au.id = app_user_id;

  return next;
end;
$$;

revoke all on function private.claim_app_access_impl() from public, anon;
grant execute on function private.claim_app_access_impl() to authenticated;

drop policy if exists app_users_select_self on public.app_users;

create policy app_users_select_self
on public.app_users
for select
to authenticated
using (
  auth_user_id = (select auth.uid())
  or (
    (select private.is_google_user())
    and auth_user_id is null
    and auth_method = 'google'
    and lower(btrim(email)) = (select private.current_user_email())
  )
);

comment on column public.app_users.username is
  'Nome de usuário opcional para acesso secundário. Nunca armazena senha.';
comment on column public.app_users.auth_method is
  'Método principal de autenticação: google para contas mestre; password para acesso secundário.';

commit;
