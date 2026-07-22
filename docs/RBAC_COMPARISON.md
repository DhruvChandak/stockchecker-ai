# RBAC Prompt Comparison

The pasted RBAC prompt is a stronger enterprise target than the current MVP. The current project already covers about 35-40% of that direction:

- JWT login, BCrypt passwords, tenant context, and tenant-scoped repositories.
- Basic tenant roles: `OWNER`, `ADMIN`, `MANAGER`, `STAFF`, `VIEWER`, `CUSTOMER_USER`.
- Customer portal isolation through linked customer membership.
- Audit logs for stock movements, imports, payments, and selected sensitive actions.
- Role-aware web navigation.

The prompt adds capabilities that were not fully present:

- Platform roles and a platform admin surface.
- Permission catalog and role-permission matrix.
- Supplier portal role and supplier isolation.
- Branch/warehouse access lists.
- Role/permission management APIs and screens.
- Approval workflows for risky actions.
- Support access grants before platform support can inspect tenant data.

## Implemented In This Pass

- Expanded role enum to include platform, finance, warehouse, sales, purchase, auditor, customer, and supplier roles.
- Added a backend `PermissionService` with the prompt's permission catalog and default role mappings.
- Added `GET /api/auth/me/permissions` and `GET /api/auth/me/tenants`.
- Replaced broad role checks with permission checks on key endpoints:
  - products
  - stock
  - purchases
  - sales
  - customers
  - suppliers
  - imports
  - forecast/reorder
  - data quality
  - dead stock
  - AI/profit insights
  - reports export
  - audit logs
  - settings/warehouses
- Added report export audit logging.
- Updated web login/register to fetch permissions.
- Updated web navigation to hide/show panels by permission, with role fallback for demo mode.
- Updated demo API so local frontend demo mode also returns permissions.

## Still Needed For Full Prompt Completion

- Database-backed `roles`, `permissions`, `role_permissions`, and permission override tables.
- User invite and role assignment screens.
- Branch/warehouse access assignment screens and backend enforcement.
- Supplier portal APIs and UI.
- Platform admin APIs and UI.
- Approval request tables and workflows.
- Support access grant enforcement.
- Automated backend tests for every permission case.

The current implementation is a safe first production-hardening step. It gives the app a real permission vocabulary and closes the biggest UI/API mismatch without destabilizing the existing MVP.
