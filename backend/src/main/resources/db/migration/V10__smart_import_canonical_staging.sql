alter table import_plan_issues
  add column available_choices jsonb not null default '[]'::jsonb,
  add column context_json jsonb not null default '{}'::jsonb,
  add column resolution_json jsonb not null default '{}'::jsonb;

create table smart_staged_products (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, source_type varchar(48) not null,
  raw_name varchar(300), normalized_name varchar(300), unit_code varchar(40), sku varchar(100), barcode varchar(160),
  category_name varchar(180), brand_name varchar(180), hsn varchar(40), gst_percent numeric(8,2), purchase_price numeric(16,2),
  external_id varchar(180), raw_metadata_json jsonb not null default '{}'::jsonb, match_status varchar(40) not null,
  matched_product_id uuid, review_status varchar(32) not null, created_at timestamptz not null, updated_at timestamptz not null,
  created_by uuid, updated_by uuid
);
create index idx_smart_staged_products_session on smart_staged_products(tenant_id, import_session_id, normalized_name, unit_code);

create table smart_staged_parties (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, party_type varchar(24) not null,
  raw_name varchar(240), normalized_name varchar(240), gstin varchar(40), phone varchar(40), email varchar(190), opening_balance numeric(16,2),
  matched_customer_id uuid, matched_supplier_id uuid, raw_metadata_json jsonb not null default '{}'::jsonb,
  match_status varchar(40) not null, review_status varchar(32) not null, created_at timestamptz not null, updated_at timestamptz not null,
  created_by uuid, updated_by uuid
);
create index idx_smart_staged_parties_session on smart_staged_parties(tenant_id, import_session_id, normalized_name, gstin);

create table smart_staged_warehouses (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, source_warehouse_name varchar(200),
  normalized_name varchar(200), matched_warehouse_id uuid, match_status varchar(40) not null, review_status varchar(32) not null,
  created_at timestamptz not null, updated_at timestamptz not null, created_by uuid, updated_by uuid
);
create index idx_smart_staged_warehouses_session on smart_staged_warehouses(tenant_id, import_session_id, normalized_name);

create table smart_staged_units (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, source_unit_code varchar(40),
  normalized_code varchar(40), matched_unit_id uuid, match_status varchar(40) not null, review_status varchar(32) not null,
  created_at timestamptz not null, updated_at timestamptz not null, created_by uuid, updated_by uuid
);
create index idx_smart_staged_units_session on smart_staged_units(tenant_id, import_session_id, normalized_code);

create table smart_staged_stock_snapshots (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, product_name varchar(300),
  normalized_product_name varchar(300), unit_code varchar(40), warehouse_name varchar(200), snapshot_date date,
  imported_stock numeric(18,3) not null, rate numeric(16,2), stock_value numeric(18,2), matched_product_id uuid,
  matched_warehouse_id uuid, current_stock numeric(18,3) not null default 0, delta_preview numeric(18,3) not null default 0,
  action varchar(48) not null, raw_metadata_json jsonb not null default '{}'::jsonb, match_status varchar(40) not null,
  review_status varchar(32) not null, created_at timestamptz not null, updated_at timestamptz not null, created_by uuid, updated_by uuid
);
create index idx_smart_staged_snapshots_session on smart_staged_stock_snapshots(tenant_id, import_session_id, normalized_product_name);

create table smart_staged_vouchers (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, voucher_type varchar(60),
  voucher_number varchar(120), voucher_date date, party_name varchar(240), normalized_party_name varchar(240), total_amount numeric(18,2),
  external_id varchar(180), fingerprint varchar(128), matched_party_id uuid, stock_impact_mode_suggestion varchar(60),
  raw_metadata_json jsonb not null default '{}'::jsonb, match_status varchar(40) not null, review_status varchar(32) not null,
  created_at timestamptz not null, updated_at timestamptz not null, created_by uuid, updated_by uuid
);
create index idx_smart_staged_vouchers_session on smart_staged_vouchers(tenant_id, import_session_id, fingerprint);

create table smart_staged_voucher_items (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  staged_voucher_id uuid not null references smart_staged_vouchers(id), session_file_id uuid not null references import_session_files(id),
  source_row_number integer not null, product_name varchar(300), normalized_product_name varchar(300), unit_code varchar(40),
  quantity numeric(18,3), rate numeric(16,2), amount numeric(18,2), warehouse_name varchar(200), matched_product_id uuid,
  matched_warehouse_id uuid, rate_source varchar(60), raw_metadata_json jsonb not null default '{}'::jsonb,
  match_status varchar(40) not null, review_status varchar(32) not null, created_at timestamptz not null, updated_at timestamptz not null,
  created_by uuid, updated_by uuid
);
create index idx_smart_staged_voucher_items_session on smart_staged_voucher_items(tenant_id, import_session_id, staged_voucher_id);

create table smart_staged_cashbook_entries (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, entry_date date,
  party_name varchar(240), amount numeric(18,2), direction varchar(24) not null, matched_party_id uuid, matched_invoice_id uuid,
  raw_metadata_json jsonb not null default '{}'::jsonb, match_status varchar(40) not null, review_status varchar(32) not null,
  created_at timestamptz not null, updated_at timestamptz not null, created_by uuid, updated_by uuid
);
create index idx_smart_staged_cashbook_session on smart_staged_cashbook_entries(tenant_id, import_session_id, entry_date);

create table smart_staged_stock_ageing (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  session_file_id uuid not null references import_session_files(id), source_row_number integer not null, product_name varchar(300),
  normalized_product_name varchar(300), unit_code varchar(40), quantity numeric(18,3), ageing_bucket varchar(100), days_old integer,
  stock_value numeric(18,2), matched_product_id uuid, raw_metadata_json jsonb not null default '{}'::jsonb,
  match_status varchar(40) not null, review_status varchar(32) not null, created_at timestamptz not null, updated_at timestamptz not null,
  created_by uuid, updated_by uuid
);
create index idx_smart_staged_ageing_session on smart_staged_stock_ageing(tenant_id, import_session_id, normalized_product_name);

create table smart_import_resolutions (
  id uuid primary key, tenant_id uuid not null references tenants(id), import_session_id uuid not null references import_sessions(id),
  resolution_key varchar(500) not null, action varchar(60) not null, target_id uuid, details_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null, updated_at timestamptz not null, created_by uuid, updated_by uuid,
  unique (tenant_id, import_session_id, resolution_key)
);
create index idx_smart_import_resolutions_session on smart_import_resolutions(tenant_id, import_session_id);
