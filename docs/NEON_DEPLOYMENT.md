# Neon PostgreSQL Deployment

StockPilot's browser and mobile applications must never connect directly to Neon. The supported data path is:

```text
Next.js web / Expo mobile -> HTTPS Spring Boot API -> Neon PostgreSQL
```

## 1. Create the Neon database

1. Create a Neon project and production branch.
2. In the Neon dashboard, click **Connect** and select the branch, database, and role.
3. Copy both connection strings:
   - **Pooled** endpoint (`-pooler` in the hostname) for normal backend traffic.
   - **Direct** endpoint (no `-pooler`) for Flyway and administrative tools.
4. Keep `sslmode=require` in both URLs. Do not commit the real URLs or password.

Neon documents pooled endpoints and recommends a direct connection for schema migrations: <https://neon.com/docs/connect/connection-pooling>.

Spring uses JDBC URLs. Convert Neon's connection string as follows:

```text
postgresql://user:password@ep-example-pooler.region.aws.neon.tech/neondb?sslmode=require
```

becomes:

```text
jdbc:postgresql://ep-example-pooler.region.aws.neon.tech/neondb?sslmode=require
```

Keep the username and password in their separate environment variables. This avoids URL parsing problems when a password contains reserved characters.

## 2. Configure the backend

Use [.env.neon.example](../.env.neon.example) as the deployment-variable checklist. Required values are:

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=<pooled JDBC URL>
DATABASE_USERNAME=<Neon role>
DATABASE_PASSWORD=<Neon role password>
FLYWAY_URL=<direct JDBC URL>
FLYWAY_USERNAME=<Neon role>
FLYWAY_PASSWORD=<Neon role password>
JWT_SECRET=<at least 64 random characters>
CORS_ALLOWED_ORIGINS=https://your-web-domain.example
CACHE_TYPE=simple
APP_DEMO_SEED_ENABLED=false
APP_DEV_TOOLS_ENABLED=false
SWAGGER_PUBLIC_ENABLED=false
APP_AUTH_REQUIRE_EMAIL_VERIFICATION=true
APP_PUBLIC_URL=https://your-web-domain.example
BACKEND_PUBLIC_URL=https://your-api-domain.example
EMAIL_PROVIDER=resend
RESEND_API_KEY=<provider secret>
EMAIL_FROM=StockPilot AI <noreply@yourdomain.com>
GOOGLE_CLIENT_ID=<optional Google OAuth Web client ID>
LOCAL_STORAGE_ROOT=/app/uploads
```

The `prod` profile fails startup when required values are absent. It also forces demo seeding, dev tools, and public Swagger off. On Render, add these values under **Service > Environment**; do not place them in a committed `.env` file.

Generate a fresh JWT secret in Windows PowerShell without changing execution policy:

```powershell
$bytes = New-Object byte[] 64
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Store the output only as Render's `JWT_SECRET`. If a database password or JWT secret has ever appeared in Git, rotate it in Neon/Render before deploying; deleting it from the latest file does not remove it from Git history.

For one small, continuously running backend instance, using the direct URL for both `DATABASE_URL` and `FLYWAY_URL` is also acceptable. Use the pooled runtime URL when running several backend instances or when connection count becomes important.

Build and start the backend from `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target\stockpilot-ai-backend-0.1.0.jar
```

Flyway runs automatically at startup. Verify the deployment with:

```text
GET https://your-api-domain.example/actuator/health
```

The response should report `"status":"UP"`.

## 3. Seed demo data once

The repository's demo seeder is repeat-safe and uses application services to create stock-ledger movements correctly. Run it against a separate Neon demo branch or database, not a production customer database.

The simplest Windows flow is:

```powershell
.\scripts\seed-neon-demo.cmd
```

When prompted, paste the complete **direct** connection string copied from Neon's **Connect** dialog. It normally looks like `postgresql://role:password@ep-example.region.aws.neon.tech/neondb?sslmode=require`. The script extracts the role and password without writing them to the repository.

Alternatively, CI or advanced users can set `NEON_DATABASE_URL`, `NEON_DATABASE_USERNAME`, and `NEON_DATABASE_PASSWORD` before running the PowerShell script directly. The script runs Flyway, invokes `DemoDataSeeder`, waits for the transaction to finish, and stops the temporary backend process. Running it again does not recreate the main 90-day transaction history.

Demo credentials:

```text
owner@demo.com / password123
ravi@demo.com / password123
```

If environment variables were used, clear them after seeding:

```powershell
Remove-Item Env:NEON_DATABASE_PASSWORD
Remove-Item Env:NEON_DATABASE_URL
Remove-Item Env:NEON_DATABASE_USERNAME
```

Never enable `APP_DEMO_SEED_ENABLED` permanently on a production deployment.

## 4. Configure and deploy the web application

The web deployment needs only the public backend URL:

```text
NEXT_PUBLIC_API_URL=https://your-api-domain.example
NEXT_PUBLIC_DEMO_MODE=false
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<optional Google OAuth Web client ID>
```

Build from `web/`:

```powershell
npm.cmd ci
npm.cmd run build
```

Add the final web origin to the backend's `CORS_ALLOWED_ORIGINS`. For multiple trusted origins, use a comma-separated list. Do not add Neon credentials to Vercel or expose them with a `NEXT_PUBLIC_` prefix.

For the current hosted domains, the corresponding public values are:

```text
# Render backend
CORS_ALLOWED_ORIGINS=https://stockcheckerai.vercel.app
APP_PUBLIC_URL=https://stockcheckerai.vercel.app
BACKEND_PUBLIC_URL=https://stockchecker-ai.onrender.com

# Vercel web
NEXT_PUBLIC_API_URL=https://stockchecker-ai.onrender.com
NEXT_PUBLIC_DEMO_MODE=false
```

Vercel variables belong under **Project Settings > Environment Variables** and require a new deployment after changes. The Render backend URL does not belong in `CORS_ALLOWED_ORIGINS`; CORS contains browser frontend origins only.

The current object-storage implementation writes to the local filesystem. Render's filesystem is ephemeral unless a persistent disk is mounted. Mount a Render disk at `/app/uploads` (or set `LOCAL_STORAGE_ROOT` to its mount path) before relying on uploaded import files across restarts.

## 5. Configure mobile builds

Mobile builds use the same public API:

```text
EXPO_PUBLIC_API_URL=https://your-api-domain.example
EXPO_PUBLIC_DEMO_MODE=false
```

The URL must be public HTTPS; `localhost` and `127.0.0.1` refer to the phone itself.

## 6. Production checks

1. Backend health is `UP`.
2. Flyway schema history contains migrations through the latest repository version.
3. Registration and login work through the deployed web origin.
4. Browser network requests target the backend domain, not Neon.
5. `APP_DEMO_SEED_ENABLED`, dev tools, and public Swagger are disabled.
6. Database, JWT, and email secrets exist only in the backend hosting provider's secret store.
7. Render has `SPRING_PROFILES_ACTIVE=prod` and Vercel has `NEXT_PUBLIC_DEMO_MODE=false`.
8. Uploaded files use a persistent Render disk or another durable object-storage implementation.
9. Create a Neon branch before testing destructive migrations or imports.

Neon requires encrypted connections and provides the connection string from its dashboard's Connect modal: <https://neon.com/docs/connect/query-with-psql-editor>.
