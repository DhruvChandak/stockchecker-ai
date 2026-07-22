alter table tenants
  add column if not exists status varchar(24) not null default 'ACTIVE',
  add column if not exists deleted_at timestamptz;

create index if not exists idx_tenants_status on tenants(status);
