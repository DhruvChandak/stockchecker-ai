create table tenants (
  id uuid primary key,
  name varchar(160) not null,
  business_mode varchar(24) not null,
  currency varchar(8) not null default 'INR',
  gst_enabled boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table app_users (
  id uuid primary key,
  email varchar(190) not null unique,
  password_hash varchar(255) not null,
  full_name varchar(160) not null,
  active boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table user_tenant_memberships (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  user_id uuid not null references app_users(id),
  role varchar(24) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, user_id)
);

create table branches (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(160) not null,
  city varchar(120),
  active boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table product_categories (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(160) not null,
  parent_id uuid,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, name)
);

create table brands (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(160) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, name)
);

create table units_of_measure (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  code varchar(24) not null,
  name varchar(80) not null,
  base_unit boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, code)
);

create table products (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  sku varchar(80),
  name varchar(220) not null,
  normalized_name varchar(220),
  category_id uuid references product_categories(id),
  brand_id uuid references brands(id),
  base_unit_id uuid references units_of_measure(id),
  hsn_code varchar(32),
  gst_percentage numeric(8,2) not null default 0,
  default_purchase_price numeric(14,2) not null default 0,
  default_sales_price numeric(14,2) not null default 0,
  reorder_point numeric(14,3) not null default 0,
  safety_stock numeric(14,3) not null default 0,
  lead_time_days integer not null default 7,
  minimum_order_quantity numeric(14,3) not null default 0,
  active boolean not null default true,
  raw_metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, sku)
);

create table unit_conversions (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid references products(id),
  from_unit_id uuid not null references units_of_measure(id),
  to_unit_id uuid not null references units_of_measure(id),
  multiplier numeric(16,6) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table product_barcodes (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  barcode varchar(120) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, barcode)
);

create table product_batches (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  batch_no varchar(120),
  expiry_date date,
  manufacturing_date date,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table product_tax_infos (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  hsn_code varchar(32),
  gst_percentage numeric(8,2) not null default 0,
  cess_percentage numeric(8,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table reorder_settings (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  warehouse_id uuid,
  reorder_point numeric(14,3) not null,
  safety_stock numeric(14,3) not null,
  lead_time_days integer not null,
  minimum_order_quantity numeric(14,3) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table warehouses (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  branch_id uuid references branches(id),
  name varchar(160) not null,
  code varchar(40),
  address text,
  active boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, name)
);

create table stock_movements (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  warehouse_id uuid not null references warehouses(id),
  batch_id uuid references product_batches(id),
  movement_type varchar(32) not null,
  quantity numeric(14,3) not null,
  unit_id uuid references units_of_measure(id),
  base_quantity numeric(14,3) not null,
  rate numeric(14,2) not null default 0,
  total_value numeric(14,2) not null default 0,
  reference_type varchar(60),
  reference_id uuid,
  movement_date timestamptz not null,
  notes text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_stock_movement_tenant_product_warehouse on stock_movements(tenant_id, product_id, warehouse_id);
create index idx_stock_movement_tenant_date on stock_movements(tenant_id, movement_date);

create table suppliers (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(180) not null,
  phone varchar(40),
  email varchar(190),
  gstin varchar(40),
  credit_days integer not null default 0,
  opening_balance numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table customers (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(180) not null,
  phone varchar(40),
  email varchar(190),
  gstin varchar(40),
  credit_limit numeric(14,2) not null default 0,
  opening_balance numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table customer_groups (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(120) not null,
  discount_percentage numeric(8,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table customer_price_lists (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  customer_id uuid references customers(id),
  product_id uuid not null references products(id),
  price numeric(14,2) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table purchase_orders (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  supplier_id uuid references suppliers(id),
  order_number varchar(80),
  status varchar(40) not null default 'OPEN',
  total_amount numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table purchase_invoices (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  supplier_id uuid references suppliers(id),
  warehouse_id uuid not null references warehouses(id),
  invoice_number varchar(80) not null,
  invoice_date date not null,
  subtotal numeric(14,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  total_amount numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, invoice_number)
);

create table purchase_invoice_items (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  purchase_invoice_id uuid not null references purchase_invoices(id),
  product_id uuid not null references products(id),
  quantity numeric(14,3) not null,
  unit_id uuid references units_of_measure(id),
  rate numeric(14,2) not null,
  tax_percentage numeric(8,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  line_total numeric(14,2) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table supplier_payments (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  supplier_id uuid not null references suppliers(id),
  amount numeric(14,2) not null,
  payment_date date not null,
  notes text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table sales_orders (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  customer_id uuid references customers(id),
  order_number varchar(80),
  status varchar(40) not null default 'OPEN',
  total_amount numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table sales_invoices (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  customer_id uuid references customers(id),
  warehouse_id uuid not null references warehouses(id),
  invoice_number varchar(80) not null,
  invoice_date date not null,
  subtotal numeric(14,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  discount_amount numeric(14,2) not null default 0,
  total_amount numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid,
  unique (tenant_id, invoice_number)
);

create table sales_invoice_items (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  sales_invoice_id uuid not null references sales_invoices(id),
  product_id uuid not null references products(id),
  quantity numeric(14,3) not null,
  unit_id uuid references units_of_measure(id),
  rate numeric(14,2) not null,
  cost_rate numeric(14,2) not null default 0,
  tax_percentage numeric(8,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  discount_amount numeric(14,2) not null default 0,
  line_total numeric(14,2) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table delivery_challans (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  customer_id uuid references customers(id),
  sales_invoice_id uuid references sales_invoices(id),
  challan_number varchar(80),
  status varchar(40) not null default 'OPEN',
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table customer_payments (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  amount numeric(14,2) not null,
  payment_date date not null,
  notes text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table import_batches (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  source_type varchar(40) not null,
  status varchar(40) not null,
  original_file_name varchar(255),
  mapping_json jsonb not null default '{}'::jsonb,
  row_count integer not null default 0,
  valid_count integer not null default 0,
  error_count integer not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table import_files (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  file_name varchar(255) not null,
  content_type varchar(120),
  storage_key varchar(500) not null,
  size_bytes bigint not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table import_mapping_templates (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(160) not null,
  source_type varchar(40) not null,
  mapping_json jsonb not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table import_errors (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  row_number integer not null,
  field_name varchar(120),
  error_code varchar(80) not null,
  message text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table staging_products (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  row_number integer not null,
  product_name varchar(220),
  sku varchar(80),
  category varchar(160),
  brand varchar(160),
  unit_code varchar(24),
  barcode varchar(120),
  hsn_code varchar(32),
  gst_percentage numeric(8,2),
  opening_stock numeric(14,3),
  purchase_price numeric(14,2),
  sales_price numeric(14,2),
  warehouse_name varchar(160),
  raw_metadata jsonb not null default '{}'::jsonb,
  committed boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table staging_customers (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  row_number integer not null,
  name varchar(180),
  phone varchar(40),
  email varchar(190),
  gstin varchar(40),
  raw_metadata jsonb not null default '{}'::jsonb,
  committed boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table staging_suppliers (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  row_number integer not null,
  name varchar(180),
  phone varchar(40),
  email varchar(190),
  gstin varchar(40),
  raw_metadata jsonb not null default '{}'::jsonb,
  committed boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table staging_invoices (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  row_number integer not null,
  invoice_type varchar(24),
  invoice_number varchar(80),
  party_name varchar(180),
  invoice_date date,
  total_amount numeric(14,2),
  raw_metadata jsonb not null default '{}'::jsonb,
  committed boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table staging_stock_movements (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  import_batch_id uuid not null references import_batches(id),
  row_number integer not null,
  product_name varchar(220),
  warehouse_name varchar(160),
  movement_type varchar(32),
  quantity numeric(14,3),
  rate numeric(14,2),
  movement_date date,
  raw_metadata jsonb not null default '{}'::jsonb,
  committed boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table forecast_results (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  warehouse_id uuid references warehouses(id),
  average_daily_demand numeric(14,3) not null,
  weighted_daily_demand numeric(14,3) not null,
  next_7_days_demand numeric(14,3) not null,
  next_30_days_demand numeric(14,3) not null,
  current_stock numeric(14,3) not null,
  stockout_date date,
  generated_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table reorder_suggestions (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  warehouse_id uuid references warehouses(id),
  reorder_point numeric(14,3) not null,
  suggested_quantity numeric(14,3) not null,
  reason text not null,
  status varchar(40) not null default 'OPEN',
  generated_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table dead_stock_insights (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  product_id uuid not null references products(id),
  warehouse_id uuid references warehouses(id),
  stock_quantity numeric(14,3) not null,
  stock_value numeric(14,2) not null,
  last_sold_date date,
  suggested_action varchar(120) not null,
  explanation text not null,
  generated_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table profit_insights (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  period_start date not null,
  period_end date not null,
  revenue numeric(14,2) not null,
  gross_profit numeric(14,2) not null,
  explanation text not null,
  evidence_json jsonb not null default '{}'::jsonb,
  generated_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table ai_conversations (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  user_id uuid references app_users(id),
  title varchar(180),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table ai_messages (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  conversation_id uuid not null references ai_conversations(id),
  role varchar(24) not null,
  content text not null,
  evidence_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table audit_logs (
  id uuid primary key,
  tenant_id uuid,
  actor_user_id uuid,
  action varchar(80) not null,
  entity_type varchar(80),
  entity_id uuid,
  details_json jsonb not null default '{}'::jsonb,
  ip_address varchar(80),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);
