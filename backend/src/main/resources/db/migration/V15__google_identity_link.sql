alter table app_users
    add column if not exists google_subject varchar(255);

create unique index if not exists idx_app_users_google_subject
    on app_users (google_subject)
    where google_subject is not null;
