create table private.categories_backup_20260922_before_restore_illustrations
as
select *
from public.categories;

revoke all on table private.categories_backup_20260922_before_restore_illustrations
from public, anon, authenticated;
