#!/usr/bin/env bash
# Creates the local development PostgreSQL role and database used by the backend.
# The connection user must be a PostgreSQL superuser or have CREATEROLE/CREATEDB.
set -euo pipefail

app_user="${APP_DATABASE_USERNAME:-stockpilot}"
app_password="${APP_DATABASE_PASSWORD:-stockpilot}"
app_database="${APP_DATABASE_NAME:-stockpilot}"
bootstrap_user="${PG_BOOTSTRAP_USER:-$(id -un)}"
bootstrap_host="${PGHOST:-127.0.0.1}"
bootstrap_port="${PGPORT:-5432}"

psql \
  --host "$bootstrap_host" \
  --port "$bootstrap_port" \
  --username "$bootstrap_user" \
  --dbname postgres \
  --set ON_ERROR_STOP=1 \
  --set app_user="$app_user" \
  --set app_password="$app_password" \
  --set app_database="$app_database" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'app_user', :'app_password')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'app_database', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'app_database')
\gexec
SQL

echo "Local PostgreSQL database '$app_database' is ready for role '$app_user'."
