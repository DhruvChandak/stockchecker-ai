# StockPilot AI Engineering Guide

## Coding Standards
- Java code targets Java 21 and Spring Boot 3.x.
- Controllers return DTOs or response maps, not JPA entities.
- Business records must carry `tenant_id`; never trust a tenant ID supplied by the client.
- Inventory source of truth is `stock_movements`; do not add or rely on `product.quantity`.
- Frontend and mobile TypeScript should keep API calls in shared client modules.
- No hardcoded secrets, API keys, or production credentials.

## Architecture
- `backend`: Spring Boot REST API, JWT security, JPA, Flyway, imports, forecasting, local AI.
- `web`: Next.js admin dashboard using TanStack Query and Tailwind.
- `mobile`: Expo app for staff workflows using the same backend APIs.
- `docker-compose.yml`: Postgres, Redis, RabbitMQ, MinIO, Adminer, and backend.

## Backend Commands
- Run tests: `mvn test`
- Run API locally: `mvn spring-boot:run`
- API docs: `http://localhost:8080/swagger-ui.html`

## Web Commands
- Install: `npm install`
- Run: `npm run dev`
- Build: `npm run build`
- Tests: `npm test`

## Mobile Commands
- Install: `npm install`
- Run: `npm start`
- Typecheck: `npm run typecheck`

## Database Migration Rules
- Use Flyway migrations under `backend/src/main/resources/db/migration`.
- Migrations must be append-only after merge.
- Add indexes with the feature that needs them.
- Keep migration names descriptive: `V2__add_feature.sql`.

## API Conventions
- All business APIs require JWT auth except `/api/auth/register` and `/api/auth/login`.
- List endpoints use pagination when growth is expected.
- Errors use the global response shape with `timestamp`, `status`, `error`, `message`, `fieldErrors`, and `path`.
- Write endpoints should validate request DTOs and audit important actions.

## Tenant Isolation Rules
- Query by `tenantId` for every business-owned record.
- Never expose another tenant's product, invoice, stock movement, import batch, or AI conversation.
- Tests must cover cross-tenant access for new shared services.

## Import Rules
- Uploaded rows go to staging tables first.
- Validation errors are stored in `import_errors`.
- Commit is the only step that writes final products, parties, invoices, or stock movements.
- Unknown fields belong in `raw_metadata` for debugging.

## Security
- Passwords are BCrypt hashes.
- JWT secret must come from the environment in production.
- Demo credentials are local-only.
- External AI/OCR providers must be configured through environment variables and never committed.
