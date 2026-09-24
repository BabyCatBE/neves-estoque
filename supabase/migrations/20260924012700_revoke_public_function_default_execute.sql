-- Neves Estoque
-- Complemento do hardening da v0.11.4.
-- PostgreSQL concede EXECUTE em funções novas para PUBLIC por padrão global.
-- O revoke global abaixo garante que funções futuras também sejam deny-by-default.
-- Cada RPC futura deve receber GRANT EXECUTE explícito na própria migration.

alter default privileges for role postgres
  revoke execute on functions from public, anon, authenticated, service_role;
