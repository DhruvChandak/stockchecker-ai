create table import_dry_runs (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_session_id uuid not null references import_sessions(id),
  status varchar(24) not null,
  strategy varchar(40) not null,
  started_at timestamptz not null,
  finished_at timestamptz,
  summary_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_import_dry_runs_session
  on import_dry_runs(tenant_id, import_session_id, started_at desc);

create table import_dry_run_items (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_session_id uuid not null references import_sessions(id),
  dry_run_id uuid not null references import_dry_runs(id) on delete cascade,
  item_type varchar(40) not null,
  action varchar(32) not null,
  source_file_id uuid,
  source_row_number integer,
  target_entity_id uuid,
  preview_json jsonb not null default '{}'::jsonb,
  warning_code varchar(100),
  error_code varchar(100),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_import_dry_run_items_filters
  on import_dry_run_items(tenant_id, dry_run_id, item_type, action);
create index idx_import_dry_run_items_source
  on import_dry_run_items(tenant_id, dry_run_id, source_file_id);
