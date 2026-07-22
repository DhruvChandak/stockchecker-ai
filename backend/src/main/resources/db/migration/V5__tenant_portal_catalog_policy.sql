alter table tenants
  add column if not exists portal_show_all_active_products boolean not null default false;
