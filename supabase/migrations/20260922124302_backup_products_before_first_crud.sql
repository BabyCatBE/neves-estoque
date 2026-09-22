create table private.products_backup_20260922_before_first_crud
as
select *
from public.products;

revoke all on table private.products_backup_20260922_before_first_crud
from public, anon, authenticated;
