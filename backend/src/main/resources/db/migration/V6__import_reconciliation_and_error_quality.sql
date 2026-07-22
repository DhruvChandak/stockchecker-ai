alter table import_batches
  add column if not exists committed_at timestamptz;

alter table import_errors
  add column if not exists entity_type varchar(60),
  add column if not exists severity varchar(16) not null default 'ERROR',
  add column if not exists raw_value text,
  add column if not exists suggested_fix text;
