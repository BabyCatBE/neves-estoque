create table private.products_backup_20260922_before_trash_restore
as
select *
from public.products;

revoke all on table private.products_backup_20260922_before_trash_restore
from public, anon, authenticated;
