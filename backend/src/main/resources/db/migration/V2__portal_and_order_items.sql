alter table user_tenant_memberships
  add column customer_id uuid references customers(id);

create table sales_order_items (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  sales_order_id uuid not null references sales_orders(id),
  product_id uuid not null references products(id),
  quantity numeric(14,3) not null,
  unit_id uuid references units_of_measure(id),
  rate numeric(14,2) not null default 0,
  line_total numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create table purchase_order_items (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  purchase_order_id uuid not null references purchase_orders(id),
  product_id uuid not null references products(id),
  warehouse_id uuid references warehouses(id),
  quantity numeric(14,3) not null,
  unit_id uuid references units_of_measure(id),
  rate numeric(14,2) not null default 0,
  line_total numeric(14,2) not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by uuid,
  updated_by uuid
);

create index idx_sales_order_items_tenant_order on sales_order_items(tenant_id, sales_order_id);
create index idx_purchase_order_items_tenant_order on purchase_order_items(tenant_id, purchase_order_id);
