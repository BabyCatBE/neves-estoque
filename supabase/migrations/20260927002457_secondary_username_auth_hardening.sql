begin;

update public.app_users
set email = username || '@usuarios.neves.invalid'
where auth_method = 'password'
  and auth_user_id is null
  and username is not null;

alter table public.app_users
  drop constraint if exists app_users_secondary_username_required,
  add constraint app_users_secondary_username_required check (
    auth_method <> 'password' or username is not null
  );

alter table public.app_users
  drop constraint if exists app_users_secondary_internal_email,
  add constraint app_users_secondary_internal_email check (
    auth_method <> 'password'
    or lower(btrim(email)) = username || '@usuarios.neves.invalid'
  );

drop function if exists public.resolve_secondary_login(text);
drop function if exists private.resolve_secondary_login_impl(text);
revoke usage on schema private from anon;

comment on column public.app_users.email is
  'E-mail real para contas Google; identificador interno derivado do username para acessos por senha.';

commit;
