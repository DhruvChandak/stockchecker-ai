create table import_sessions (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(160) not null,
  status varchar(40) not null,
  recommended_strategy varchar(40) not null,
  committed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_import_sessions_tenant_created
  on import_sessions(tenant_id, created_at desc);

create table import_session_files (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_session_id uuid not null references import_sessions(id),
  original_file_name varchar(255) not null,
  content_type varchar(120),
  storage_key varchar(500) not null,
  file_hash varchar(64) not null,
  detected_file_type varchar(48) not null,
  selected_file_type varchar(48),
  confidence numeric(5,4) not null default 0,
  detection_reason text,
  status varchar(32) not null,
  row_count integer not null default 0,
  error_count integer not null default 0,
  warning_count integer not null default 0,
  date_range_start date,
  date_range_end date,
  company_name varchar(220),
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_import_session_files_tenant_session
  on import_session_files(tenant_id, import_session_id, created_at);
create index idx_import_session_files_hash
  on import_session_files(tenant_id, import_session_id, file_hash);

create table import_plans (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_session_id uuid not null references import_sessions(id),
  strategy varchar(40) not null,
  plan_json jsonb not null default '{}'::jsonb,
  status varchar(32) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, import_session_id)
);

create table import_plan_issues (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_session_id uuid not null references import_sessions(id),
  severity varchar(32) not null,
  code varchar(100) not null,
  message text not null,
  affected_file_id uuid references import_session_files(id),
  affected_rows jsonb not null default '[]'::jsonb,
  suggested_action text,
  resolved boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_import_plan_issues_tenant_session
  on import_plan_issues(tenant_id, import_session_id, severity, created_at);
