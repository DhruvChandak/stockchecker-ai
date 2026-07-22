create table payment_reminders (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  amount_due numeric(14,2) not null default 0,
  reminder_date date not null,
  reminder_time varchar(16),
  status varchar(24) not null default 'OPEN',
  notes text,
  completed_at timestamptz,
  completed_payment_id uuid,
  created_by uuid,
  updated_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index idx_payment_reminders_tenant_date on payment_reminders(tenant_id, reminder_date);
create index idx_payment_reminders_tenant_customer on payment_reminders(tenant_id, customer_id);
create index idx_payment_reminders_tenant_status on payment_reminders(tenant_id, status);
