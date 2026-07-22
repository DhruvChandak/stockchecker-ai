alter table import_batches
  add column if not exists import_purpose varchar(40) not null default 'MASTER_IMPORT',
  add column if not exists rolled_back_at timestamptz;

create table if not exists import_effects (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  entity_type varchar(40) not null,
  entity_id uuid not null,
  action varchar(24) not null,
  old_value_json jsonb not null default '{}'::jsonb,
  new_value_json jsonb not null default '{}'::jsonb,
  reversible boolean not null default true,
  reversed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index if not exists idx_import_effects_batch on import_effects(tenant_id, import_batch_id);
create index if not exists idx_import_effects_entity on import_effects(tenant_id, entity_type, entity_id);
