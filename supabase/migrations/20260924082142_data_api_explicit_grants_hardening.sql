-- Neves Estoque — reconciliação do hardening da Data API.
-- Mantém exatamente a superfície atual e torna grants/defaults explícitos.
-- Complementa 20260924012247_explicit_data_api_grants e
-- 20260924012700_revoke_public_function_default_execute.

alter default privileges for role postgres in schema public
  revoke all on tables from anon, authenticated, service_role;

alter default privileges for role postgres in schema public
  revoke all on sequences from anon, authenticated, service_role;

alter default privileges for role postgres in schema public
  revoke execute on functions from public, anon, authenticated, service_role;

revoke all on all tables in schema public from anon, authenticated, service_role;

grant select on table public.app_users to authenticated;
grant select on table public.devices to authenticated;
grant select, insert, update on table public.categories to authenticated;
grant select, insert, update on table public.products to authenticated;
grant select, insert, update on table public.suppliers to authenticated;
grant select on table public.entries to authenticated;
grant select on table public.entry_items to authenticated;
grant select on table public.conferences to authenticated;
grant select on table public.conference_items to authenticated;
grant select on table public.audit_log to authenticated;
grant select on table public.stock_current to authenticated;

grant select, insert, update, delete on table
  public.app_users,
  public.devices,
  public.categories,
  public.products,
  public.suppliers,
  public.entries,
  public.entry_items,
  public.conferences,
  public.conference_items,
  public.audit_log
to service_role;

grant select on table public.stock_current to service_role;

revoke all on all sequences in schema public from anon, authenticated, service_role;
grant usage, select, update on sequence public.audit_log_id_seq to service_role;

revoke execute on all functions in schema public from public, anon, authenticated, service_role;

grant execute on function public.claim_app_access() to authenticated, service_role;
grant execute on function public.register_device(uuid, text) to authenticated, service_role;
grant execute on function public.create_entry(uuid, timestamptz, uuid, uuid, jsonb, text) to authenticated, service_role;
grant execute on function public.create_entry_with_draft_supplier(text, timestamptz, uuid, uuid, jsonb, text) to authenticated, service_role;
grant execute on function public.create_conference(timestamptz, text, uuid, uuid, jsonb, text) to authenticated, service_role;
grant execute on function public.create_product(text, uuid, text, numeric, numeric) to authenticated, service_role;
grant execute on function public.create_supplier(text, text, text, text, integer, smallint, integer, integer) to authenticated, service_role;
grant execute on function public.reorder_categories(uuid[]) to authenticated, service_role;
grant execute on function public.reorder_products(jsonb) to authenticated, service_role;
grant execute on function public.restore_entry(uuid, uuid) to authenticated, service_role;
grant execute on function public.restore_product(uuid) to authenticated, service_role;
grant execute on function public.restore_supplier(uuid) to authenticated, service_role;
grant execute on function public.soft_delete_entry(uuid, uuid) to authenticated, service_role;
grant execute on function public.soft_delete_product(uuid) to authenticated, service_role;
grant execute on function public.soft_delete_supplier(uuid) to authenticated, service_role;
grant execute on function public.update_entry(uuid, uuid, timestamptz, uuid, jsonb, text) to authenticated, service_role;
grant execute on function public.update_product_details(uuid, text, uuid) to authenticated, service_role;
grant execute on function public.update_supplier(uuid, text, text, text, text, integer, smallint, integer, integer) to authenticated, service_role;

-- RPC legado de cadastro rápido de Produto continua inacessível ao authenticated.
grant execute on function public.create_quick_entry_product(text, text, uuid, uuid) to service_role;
