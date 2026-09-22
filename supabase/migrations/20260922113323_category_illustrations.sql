alter table public.categories
  add column if not exists illustration_source text,
  add column if not exists illustration_key text,
  add column if not exists illustration_position_x smallint not null default 50,
  add column if not exists illustration_position_y smallint not null default 50;

alter table public.categories
  drop constraint if exists categories_illustration_source_check,
  drop constraint if exists categories_illustration_pair_check,
  drop constraint if exists categories_illustration_position_x_check,
  drop constraint if exists categories_illustration_position_y_check,
  drop constraint if exists categories_illustration_library_key_check,
  drop constraint if exists categories_illustration_upload_key_check;

alter table public.categories
  add constraint categories_illustration_source_check
    check (illustration_source is null or illustration_source in ('library', 'upload')),
  add constraint categories_illustration_pair_check
    check (
      (illustration_source is null and illustration_key is null)
      or
      (illustration_source is not null and illustration_key is not null)
    ),
  add constraint categories_illustration_position_x_check
    check (illustration_position_x between 0 and 100),
  add constraint categories_illustration_position_y_check
    check (illustration_position_y between 0 and 100),
  add constraint categories_illustration_library_key_check
    check (
      illustration_source <> 'library'
      or illustration_key ~ '^[a-z0-9][a-z0-9-]{0,63}$'
    ),
  add constraint categories_illustration_upload_key_check
    check (
      illustration_source <> 'upload'
      or (
        illustration_key like id::text || '/%'
        and illustration_key ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.(jpg|png|webp)$'
      )
    );

insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'category-illustrations',
  'category-illustrations',
  false,
  5242880,
  array['image/jpeg','image/png','image/webp']::text[]
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists category_illustrations_select_authorized on storage.objects;
create policy category_illustrations_select_authorized
on storage.objects
for select
to authenticated
using (
  bucket_id = 'category-illustrations'
  and (select private.is_authorized_user())
);

drop policy if exists category_illustrations_insert_authorized on storage.objects;
create policy category_illustrations_insert_authorized
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'category-illustrations'
  and (select private.is_authorized_user())
  and array_length(string_to_array(name, '/'), 1) = 2
  and split_part(name, '/', 1) ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
  and split_part(name, '/', 2) ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(jpg|png|webp)$'
);

drop policy if exists category_illustrations_delete_authorized on storage.objects;
create policy category_illustrations_delete_authorized
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'category-illustrations'
  and (select private.is_authorized_user())
);
