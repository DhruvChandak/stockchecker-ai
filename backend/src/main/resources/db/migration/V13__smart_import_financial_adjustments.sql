alter table smart_staged_vouchers add column tax_amount numeric(18,2) not null default 0;
alter table smart_staged_vouchers add column discount_amount numeric(18,2) not null default 0;
alter table smart_staged_vouchers add column freight_amount numeric(18,2) not null default 0;
alter table smart_staged_vouchers add column round_off_amount numeric(18,2) not null default 0;
alter table smart_staged_vouchers add column other_charges_amount numeric(18,2) not null default 0;
alter table smart_staged_vouchers add column tax_line_count integer not null default 0;
alter table smart_staged_vouchers add column discount_line_count integer not null default 0;
alter table smart_staged_vouchers add column freight_line_count integer not null default 0;
alter table smart_staged_vouchers add column round_off_line_count integer not null default 0;
alter table smart_staged_vouchers add column other_charge_line_count integer not null default 0;

alter table sales_invoices add column freight_amount numeric(14,2) not null default 0;
alter table sales_invoices add column round_off_amount numeric(14,2) not null default 0;
alter table sales_invoices add column other_charges_amount numeric(14,2) not null default 0;
alter table sales_invoices add column financial_metadata jsonb not null default '{}'::jsonb;

alter table purchase_invoices add column discount_amount numeric(14,2) not null default 0;
alter table purchase_invoices add column freight_amount numeric(14,2) not null default 0;
alter table purchase_invoices add column round_off_amount numeric(14,2) not null default 0;
alter table purchase_invoices add column other_charges_amount numeric(14,2) not null default 0;
alter table purchase_invoices add column financial_metadata jsonb not null default '{}'::jsonb;

create table financial_adjustments (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  adjustment_type varchar(32) not null,
  customer_id uuid references customers(id),
  supplier_id uuid references suppliers(id),
  voucher_number varchar(120) not null,
  voucher_date date not null,
  subtotal numeric(18,2) not null default 0,
  tax_amount numeric(18,2) not null default 0,
  discount_amount numeric(18,2) not null default 0,
  freight_amount numeric(18,2) not null default 0,
  round_off_amount numeric(18,2) not null default 0,
  other_charges_amount numeric(18,2) not null default 0,
  total_amount numeric(18,2) not null default 0,
  source_external_id varchar(180),
  source_fingerprint varchar(128),
  raw_metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, adjustment_type, voucher_number)
);
create unique index uq_financial_adjustment_fingerprint
  on financial_adjustments(tenant_id, adjustment_type, source_fingerprint)
  where source_fingerprint is not null;

create table financial_adjustment_items (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  financial_adjustment_id uuid not null references financial_adjustments(id) on delete cascade,
  product_id uuid references products(id),
  unit_id uuid references units_of_measure(id),
  warehouse_id uuid references warehouses(id),
  warehouse_name varchar(200),
  quantity numeric(18,3) not null default 0,
  rate numeric(16,2) not null default 0,
  line_total numeric(18,2) not null default 0,
  raw_metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);
create index idx_financial_adjustment_items_document
  on financial_adjustment_items(tenant_id, financial_adjustment_id);
