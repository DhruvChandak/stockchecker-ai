alter table tenants
  add column if not exists allow_negative_stock boolean not null default false;
