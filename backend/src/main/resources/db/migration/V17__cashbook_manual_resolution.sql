alter table smart_staged_cashbook_entries
  add column resolution_status varchar(32) not null default 'UNRESOLVED',
  add column manual_resolution_action varchar(40),
  add column customer_payment_id uuid references customer_payments(id),
  add column supplier_payment_id uuid references supplier_payments(id),
  add column resolved_by uuid,
  add column resolved_at timestamptz,
  add column resolution_note varchar(500);

create index idx_smart_cashbook_unresolved
  on smart_staged_cashbook_entries(tenant_id, import_session_id, resolution_status, entry_date);
