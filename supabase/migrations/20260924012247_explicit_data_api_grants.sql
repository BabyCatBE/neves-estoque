-- Neves Estoque
-- Hardening para a mudança do Supabase Data API em 30/10/2026.
-- Novas entidades em public ficam deny-by-default e exigem GRANT explícito.
-- Fonte: https://github.com/orgs/supabase/discussions/45329

-- ---------------------------------------------------------------------------
-- Defaults seguros para objetos FUTUROS criados pela role postgres.
-- ---------------------------------------------------------------------------

alter default privileges for role postgres in schema public
  revoke all privileges on tables from anon, authenticated, service_role;

alter default privileges for role postgres in schema public
  revoke all privileges on sequences from anon, authenticated, service_role;

alter default privileges for role postgres in schema public
  revoke execute on functions from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Estado explícito das tabelas/views atuais.
-- ---------------------------------------------------------------------------

revoke all privileges on table
  public.app_users,
  public.audit_log,
  public.categories,
  public.conference_items,
  public.conferences,
  public.devices,
  public.entries,
  public.entry_items,
  public.products,
  public.suppliers,
  public.stock_current
from anon, authenticated, service_role;

-- Frontend autenticado: somente o necessário.
grant select on table
  public.app_users,
  public.audit_log,
  public.conference_items,
  public.conferences,
  public.devices,
  public.entries,
  public.entry_items,
  public.stock_current
to authenticated;

grant select, insert, update on table
  public.categories,
  public.products,
  public.suppliers
to authenticated;

-- service_role: CRUD explícito nas tabelas-base, sem privilégios DDL/trigger/truncate.
grant select, insert, update, delete on table
  public.app_users,
  public.audit_log,
  public.categories,
  public.conference_items,
  public.conferences,
  public.devices,
  public.entries,
  public.entry_items,
  public.products,
  public.suppliers
to service_role;

grant select on table public.stock_current to service_role;

-- ---------------------------------------------------------------------------
-- Sequence atual: nenhuma permissão para clientes.
-- ---------------------------------------------------------------------------

revoke all privileges on sequence public.audit_log_id_seq
from anon, authenticated, service_role;

grant usage, select, update on sequence public.audit_log_id_seq
to service_role;

-- ---------------------------------------------------------------------------
-- RLS permanece obrigatório em todas as tabelas public do núcleo.
-- ---------------------------------------------------------------------------

alter table public.app_users enable row level security;
alter table public.audit_log enable row level security;
alter table public.categories enable row level security;
alter table public.conference_items enable row level security;
alter table public.conferences enable row level security;
alter table public.devices enable row level security;
alter table public.entries enable row level security;
alter table public.entry_items enable row level security;
alter table public.products enable row level security;
alter table public.suppliers enable row level security;

-- REGRA PERMANENTE:
-- Toda migration futura que criar table/view/sequence/function em public deve
-- declarar na própria migration os GRANTs mínimos necessários.
-- Não restaurar defaults globais de autoexposição para anon/authenticated.
