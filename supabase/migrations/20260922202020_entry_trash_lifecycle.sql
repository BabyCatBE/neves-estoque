create or replace function private.soft_delete_entry_impl(
  p_entry_id uuid,
  p_device_id uuid
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private, auth
as $$
begin
  if not private.is_authorized_user() then
    raise exception 'Acesso não autorizado.';
  end if;

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  update public.entries
  set deleted_at = now()
  where id = p_entry_id
    and deleted_at is null;

  if not found then
    raise exception 'Entrada não encontrada ou já excluída.';
  end if;
end;
$$;

create or replace function public.soft_delete_entry(
  p_entry_id uuid,
  p_device_id uuid
)
returns void
language sql
set search_path = pg_catalog, private
as $$
  select private.soft_delete_entry_impl(p_entry_id, p_device_id);
$$;

create or replace function private.restore_entry_impl(
  p_entry_id uuid,
  p_device_id uuid
)
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

  if not private.is_device_allowed(p_device_id) then
    raise exception 'Dispositivo não autorizado.';
  end if;

  select e.restore_until
  into v_restore_until
  from public.entries e
  where e.id = p_entry_id
    and e.deleted_at is not null
  for update;

  if not found then
    raise exception 'Entrada não encontrada na lixeira.';
  end if;

  if v_restore_until is null or now() > v_restore_until then
    raise exception 'Prazo de restauração expirado.';
  end if;

  perform set_config('app.device_id', p_device_id::text, true);

  update public.entries
  set deleted_at = null
  where id = p_entry_id;
end;
$$;

create or replace function public.restore_entry(
  p_entry_id uuid,
  p_device_id uuid
)
returns void
language sql
set search_path = pg_catalog, private
as $$
  select private.restore_entry_impl(p_entry_id, p_device_id);
$$;

revoke all on function private.soft_delete_entry_impl(uuid,uuid)
from public, anon, authenticated;
grant execute on function private.soft_delete_entry_impl(uuid,uuid)
to authenticated;

revoke all on function private.restore_entry_impl(uuid,uuid)
from public, anon, authenticated;
grant execute on function private.restore_entry_impl(uuid,uuid)
to authenticated;

revoke all on function public.soft_delete_entry(uuid,uuid)
from public, anon, authenticated;
grant execute on function public.soft_delete_entry(uuid,uuid)
to authenticated;

revoke all on function public.restore_entry(uuid,uuid)
from public, anon, authenticated;
grant execute on function public.restore_entry(uuid,uuid)
to authenticated;
