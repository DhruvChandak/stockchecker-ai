alter table customer_payments
  add column sales_invoice_id uuid references sales_invoices(id),
  add column mode varchar(24) not null default 'OTHER',
  add column reference_number varchar(160),
  add column source_import_session_id uuid references import_sessions(id),
  add column source_file_id uuid references import_session_files(id),
  add column source_row_number integer,
  add column source_fingerprint varchar(64),
  add column raw_metadata_json jsonb not null default '{}'::jsonb;

create unique index uq_customer_payment_source_fingerprint
  on customer_payments(tenant_id, source_fingerprint)
  where source_fingerprint is not null;

alter table supplier_payments
  add column purchase_invoice_id uuid references purchase_invoices(id),
  add column mode varchar(24) not null default 'OTHER',
  add column reference_number varchar(160),
  add column source_import_session_id uuid references import_sessions(id),
  add column source_file_id uuid references import_session_files(id),
  add column source_row_number integer,
  add column source_fingerprint varchar(64),
  add column raw_metadata_json jsonb not null default '{}'::jsonb;

create unique index uq_supplier_payment_source_fingerprint
  on supplier_payments(tenant_id, source_fingerprint)
  where source_fingerprint is not null;

create table outstanding_snapshots (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  party_type varchar(24) not null,
  customer_id uuid references customers(id),
  supplier_id uuid references suppliers(id),
  snapshot_date date not null,
  outstanding_amount numeric(18,2) not null,
  overdue_amount numeric(18,2),
  source_import_session_id uuid not null references import_sessions(id),
  source_file_id uuid not null references import_session_files(id),
  source_row_number integer not null,
  source_fingerprint varchar(64) not null,
  raw_metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  constraint ck_outstanding_snapshot_party check (
    (party_type = 'CUSTOMER' and customer_id is not null and supplier_id is null)
    or (party_type = 'SUPPLIER' and supplier_id is not null and customer_id is null)
  )
);

create unique index uq_outstanding_snapshot_source_fingerprint
  on outstanding_snapshots(tenant_id, source_fingerprint);
create index idx_outstanding_snapshot_customer
  on outstanding_snapshots(tenant_id, customer_id, snapshot_date desc);
create index idx_outstanding_snapshot_supplier
  on outstanding_snapshots(tenant_id, supplier_id, snapshot_date desc);

alter table smart_staged_parties
  add column source_type varchar(48) not null default 'ACCOUNTING_MASTER',
  add column snapshot_date date;

alter table smart_staged_cashbook_entries
  add column gstin varchar(40),
  add column matched_party_type varchar(24) not null default 'UNKNOWN',
  add column cashbook_match_status varchar(48) not null default 'UNMATCHED_REVIEW',
  add column payment_mode varchar(24) not null default 'OTHER',
  add column reference_number varchar(160),
  add column fingerprint varchar(64),
  add column match_confidence numeric(5,2) not null default 0,
  add column match_reason varchar(500);

create index idx_smart_cashbook_fingerprint
  on smart_staged_cashbook_entries(tenant_id, import_session_id, fingerprint);
