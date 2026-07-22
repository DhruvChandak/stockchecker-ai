alter table import_dry_runs add column input_fingerprint varchar(64);

create table smart_import_commits (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_session_id uuid not null references import_sessions(id),
  dry_run_id uuid not null,
  status varchar(24) not null,
  strategy varchar(40) not null,
  started_at timestamptz not null,
  finished_at timestamptz,
  committed_by uuid,
  summary_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, import_session_id)
);

create index idx_smart_import_commits_session on smart_import_commits(tenant_id, import_session_id, started_at desc);

create table smart_import_effects (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_session_id uuid not null references import_sessions(id),
  commit_id uuid not null references smart_import_commits(id) on delete cascade,
  source_file_id uuid,
  source_row_number integer,
  entity_type varchar(40) not null,
  entity_id uuid not null,
  action varchar(32) not null,
  old_value_json jsonb not null default '{}'::jsonb,
  new_value_json jsonb not null default '{}'::jsonb,
  reversible boolean not null default false,
  reversed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_smart_import_effects_commit on smart_import_effects(tenant_id, commit_id, entity_type, action);
create index idx_smart_import_effects_entity on smart_import_effects(tenant_id, entity_type, entity_id);

create table external_record_mappings (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  source_system varchar(60) not null,
  import_session_id uuid,
  source_file_id uuid,
  entity_type varchar(60) not null,
  external_id varchar(240),
  normalized_key varchar(600),
  local_entity_type varchar(60) not null,
  local_entity_id uuid not null,
  source_hash varchar(128),
  last_seen_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, source_system, entity_type, normalized_key)
);

create index idx_external_mapping_external on external_record_mappings(tenant_id, source_system, entity_type, external_id);
create index idx_external_mapping_local on external_record_mappings(tenant_id, local_entity_type, local_entity_id);
