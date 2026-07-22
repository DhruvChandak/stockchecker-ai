alter table app_users
    add column if not exists email_verified boolean not null default true,
    add column if not exists email_verified_at timestamp with time zone,
    add column if not exists verification_token_hash varchar(128),
    add column if not exists verification_token_expires_at timestamp with time zone,
    add column if not exists verification_sent_at timestamp with time zone,
    add column if not exists verification_resend_count integer not null default 0,
    add column if not exists password_reset_token_hash varchar(128),
    add column if not exists password_reset_token_expires_at timestamp with time zone,
    add column if not exists password_reset_requested_at timestamp with time zone,
    add column if not exists password_reset_used_at timestamp with time zone;

update app_users
set email_verified = true,
    email_verified_at = coalesce(email_verified_at, created_at)
where email_verified is true
  and email_verified_at is null;

create index if not exists idx_app_users_verification_token_hash on app_users (verification_token_hash);
create index if not exists idx_app_users_password_reset_token_hash on app_users (password_reset_token_hash);
