begin;

create or replace function private.resolve_secondary_login_impl(p_username text)
returns text
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
  select au.email
  from public.app_users au
  where au.is_authorized
    and au.auth_method = 'password'
    and au.auth_user_id is not null
    and au.username = lower(btrim(coalesce(p_username, '')))
  limit 1;
$$;

revoke all on function private.resolve_secondary_login_impl(text)
from public, anon, authenticated;
grant execute on function private.resolve_secondary_login_impl(text)
to anon, authenticated;
grant usage on schema private to anon;

create or replace function public.resolve_secondary_login(p_username text)
returns text
language sql
security invoker
set search_path = pg_catalog, private
as $$
  select private.resolve_secondary_login_impl(p_username);
$$;

revoke all on function public.resolve_secondary_login(text)
from public, anon, authenticated;
grant execute on function public.resolve_secondary_login(text)
to anon, authenticated;

create or replace function private.link_secondary_auth_user()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
  if new.email is null or btrim(new.email) = '' then
    return new;
  end if;

  update public.app_users au
  set auth_user_id = new.id
  where au.is_authorized
    and au.auth_method = 'password'
    and au.auth_user_id is null
    and lower(btrim(au.email)) = lower(btrim(new.email));

  return new;
end;
$$;

revoke all on function private.link_secondary_auth_user()
from public, anon, authenticated;

drop trigger if exists neves_link_secondary_auth_user on auth.users;

create trigger neves_link_secondary_auth_user
after insert or update of email on auth.users
for each row
execute function private.link_secondary_auth_user();

commit;
