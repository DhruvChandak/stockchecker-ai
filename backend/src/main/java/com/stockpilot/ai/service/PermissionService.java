package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.repo.Repositories;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PermissionService {
    private static final List<PermissionDefinition> CATALOG = List.of(
        permission("dashboard.view", "Dashboard", "dashboard", "View dashboard metrics"),
        permission("today_actions.view", "Today's Actions", "dashboard", "View prioritized action cards"),
        permission("products.view", "View Products", "products", "View product catalog"),
        permission("products.create", "Create Products", "products", "Create products"),
        permission("products.update", "Update Products", "products", "Update products"),
        permission("products.delete", "Delete Products", "products", "Delete products"),
        permission("products.merge", "Merge Products", "products", "Merge duplicate products"),
        permission("stock.view", "View Stock", "stock", "View stock and stock movements"),
        permission("stock.adjust", "Adjust Stock", "stock", "Create stock adjustments"),
        permission("stock.transfer", "Transfer Stock", "stock", "Transfer stock between warehouses"),
        permission("stock.approve_adjustment", "Approve Stock Adjustment", "stock", "Approve sensitive stock adjustments"),
        permission("stock.view_cost", "View Stock Cost", "stock", "View cost-backed stock values"),
        permission("sales.view", "View Sales", "sales", "View sales invoices and orders"),
        permission("sales.create", "Create Sales", "sales", "Create sales invoices and orders"),
        permission("sales.update", "Update Sales", "sales", "Update sales documents"),
        permission("sales.cancel", "Cancel Sales", "sales", "Cancel sales documents"),
        permission("sales.view_margin", "View Sales Margin", "sales", "View sales margin"),
        permission("purchases.view", "View Purchases", "purchases", "View purchase documents"),
        permission("purchases.create", "Create Purchases", "purchases", "Create purchase documents"),
        permission("purchases.update", "Update Purchases", "purchases", "Update purchase documents"),
        permission("purchases.approve", "Approve Purchases", "purchases", "Approve purchase documents"),
        permission("purchases.view_cost", "View Purchase Cost", "purchases", "View purchase cost"),
        permission("customers.view", "View Customers", "customers", "View customers"),
        permission("customers.create", "Create Customers", "customers", "Create customers"),
        permission("customers.update", "Update Customers", "customers", "Update customers"),
        permission("customers.view_outstanding", "View Outstanding", "customers", "View customer receivables"),
        permission("suppliers.view", "View Suppliers", "suppliers", "View suppliers"),
        permission("suppliers.create", "Create Suppliers", "suppliers", "Create suppliers"),
        permission("suppliers.update", "Update Suppliers", "suppliers", "Update suppliers"),
        permission("imports.view", "View Imports", "imports", "View imports"),
        permission("imports.upload", "Upload Imports", "imports", "Upload import files"),
        permission("imports.map", "Map Imports", "imports", "Apply import mappings"),
        permission("imports.validate", "Validate Imports", "imports", "Validate staged imports"),
        permission("imports.commit", "Commit Imports", "imports", "Commit validated imports"),
        permission("imports.rollback", "Rollback Imports", "imports", "Undo committed imports safely"),
        permission("integrations.tally.view", "View Tally Hub", "integrations", "View Tally import status"),
        permission("integrations.tally.manage", "Manage Tally Hub", "integrations", "Manage Tally mappings and imports"),
        permission("forecast.view", "View Forecast", "forecast", "View demand forecasts"),
        permission("reorder.view", "View Reorder", "forecast", "View reorder suggestions"),
        permission("reorder.create_purchase_order", "Create Reorder PO", "forecast", "Create draft purchase orders from reorder suggestions"),
        permission("dead_stock.view", "View Dead Stock", "dead_stock", "View dead-stock insights"),
        permission("dead_stock.action", "Act On Dead Stock", "dead_stock", "Record dead-stock actions"),
        permission("data_quality.view", "View Data Quality", "data_quality", "View cleanup findings"),
        permission("data_quality.apply_suggestion", "Apply Cleanup Suggestion", "data_quality", "Apply product cleanup suggestions"),
        permission("data_quality.merge", "Merge Cleanup Products", "data_quality", "Merge duplicate products"),
        permission("insights.profit.view", "View Profit Insights", "ai", "View profit-drop analysis"),
        permission("ai.assistant.use", "Use AI Assistant", "ai", "Use the AI assistant"),
        permission("reports.view", "View Reports", "reports", "View reports"),
        permission("reports.export", "Export Reports", "reports", "Export report CSVs"),
        permission("users.view", "View Users", "admin", "View workspace users"),
        permission("users.manage", "Manage Users", "admin", "Invite and update users"),
        permission("roles.view", "View Roles", "admin", "View roles and permissions"),
        permission("roles.manage", "Manage Roles", "admin", "Manage roles and permissions"),
        permission("settings.view", "View Settings", "settings", "View business settings"),
        permission("settings.manage", "Manage Settings", "settings", "Manage business settings"),
        permission("billing.view", "View Billing", "billing", "View billing"),
        permission("billing.manage", "Manage Billing", "billing", "Manage billing"),
        permission("audit.view", "View Audit Logs", "audit", "View audit logs"),
        permission("portal.customer.view", "Customer Portal", "portal", "Access customer portal"),
        permission("portal.customer.order_create", "Customer Orders", "portal", "Create portal orders"),
        permission("portal.customer.invoice_view", "Customer Invoices", "portal", "View portal invoices"),
        permission("portal.customer.outstanding_view", "Customer Outstanding", "portal", "View portal outstanding"),
        permission("portal.supplier.view", "Supplier Portal", "portal", "Access supplier portal"),
        permission("portal.supplier.po_view", "Supplier Purchase Orders", "portal", "View supplier purchase orders"),
        permission("portal.supplier.invoice_upload", "Supplier Invoice Upload", "portal", "Upload supplier invoices"),
        permission("portal.supplier.delivery_update", "Supplier Delivery Update", "portal", "Update supplier delivery status"),
        permission("platform.tenants.view", "View Tenants", "platform", "View platform tenant list"),
        permission("platform.tenants.manage", "Manage Tenants", "platform", "Manage platform tenants"),
        permission("platform.subscriptions.manage", "Manage Subscriptions", "platform", "Manage subscriptions"),
        permission("platform.system_health.view", "System Health", "platform", "View platform health"),
        permission("platform.support_access.manage", "Support Access", "platform", "Manage support access grants"),
        permission("platform.audit.view", "Platform Audit", "platform", "View platform audit logs")
    );

    private static final EnumMap<DomainEnums.Role, Set<String>> ROLE_PERMISSIONS = buildRolePermissions();

    private final Repositories.MembershipRepository memberships;
    private final Repositories.TenantRepository tenants;

    public PermissionService(Repositories.MembershipRepository memberships, Repositories.TenantRepository tenants) {
        this.memberships = memberships;
        this.tenants = tenants;
    }

    public boolean has(String permissionCode) {
        var role = TenantContext.role();
        return role != null && permissionsFor(role).contains(permissionCode);
    }

    public Set<String> currentPermissions() {
        return permissionsFor(TenantContext.role());
    }

    public Map<String, Object> currentPermissionResponse() {
        var role = TenantContext.role();
        return Map.of(
            "role", role,
            "permissions", currentPermissions(),
            "catalog", CATALOG.stream().map(PermissionDefinition::asMap).toList()
        );
    }

    public List<Map<String, Object>> accessibleTenants(UUID userId) {
        return memberships.findByUserId(userId).stream()
            .flatMap(membership -> tenants.findByIdAndStatus(membership.tenantId, DomainEnums.TenantStatus.ACTIVE)
                .map(tenant -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tenantId", tenant.id);
                    row.put("name", tenant.name);
                    row.put("businessMode", tenant.businessMode);
                    row.put("role", membership.role);
                    row.put("customerId", membership.customerId);
                    return row;
                })
                .stream())
            .toList();
    }

    public Set<String> permissionsFor(DomainEnums.Role role) {
        if (role == null) {
            return Set.of();
        }
        return ROLE_PERMISSIONS.getOrDefault(role, Set.of());
    }

    private static PermissionDefinition permission(String code, String name, String module, String description) {
        return new PermissionDefinition(code, name, module, description);
    }

    private static EnumMap<DomainEnums.Role, Set<String>> buildRolePermissions() {
        var map = new EnumMap<DomainEnums.Role, Set<String>>(DomainEnums.Role.class);
        var tenantPermissions = CATALOG.stream()
            .map(PermissionDefinition::code)
            .filter(code -> !code.startsWith("platform."))
            .filter(code -> !code.startsWith("portal."))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        var platformPermissions = CATALOG.stream()
            .map(PermissionDefinition::code)
            .filter(code -> code.startsWith("platform."))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        map.put(DomainEnums.Role.OWNER, immutable(tenantPermissions));
        map.put(DomainEnums.Role.ADMIN, without(tenantPermissions, Set.of("billing.manage")));
        map.put(DomainEnums.Role.MANAGER, immutable(List.of(
            "dashboard.view", "today_actions.view",
            "products.view", "products.create", "products.update",
            "stock.view", "stock.transfer",
            "sales.view", "sales.create", "sales.update",
            "purchases.view", "purchases.create", "purchases.update",
            "customers.view", "customers.create", "customers.update", "customers.view_outstanding",
            "suppliers.view",
            "imports.view", "imports.upload", "imports.map", "imports.validate",
            "integrations.tally.view",
            "forecast.view", "reorder.view",
            "dead_stock.view", "dead_stock.action",
            "data_quality.view", "data_quality.apply_suggestion",
            "insights.profit.view", "ai.assistant.use",
            "reports.view"
        )));
        map.put(DomainEnums.Role.STAFF, immutable(List.of(
            "dashboard.view", "products.view", "stock.view", "stock.adjust",
            "sales.view", "sales.create", "customers.view", "customers.create",
            "imports.view", "ai.assistant.use"
        )));
        map.put(DomainEnums.Role.WAREHOUSE_STAFF, immutable(List.of(
            "dashboard.view", "products.view", "stock.view", "stock.adjust", "stock.transfer", "dead_stock.view"
        )));
        map.put(DomainEnums.Role.SALES_STAFF, immutable(List.of(
            "dashboard.view", "products.view", "stock.view", "sales.view", "sales.create",
            "customers.view", "customers.create", "customers.view_outstanding"
        )));
        map.put(DomainEnums.Role.PURCHASE_MANAGER, immutable(List.of(
            "dashboard.view", "products.view", "stock.view", "suppliers.view", "suppliers.create", "suppliers.update",
            "purchases.view", "purchases.create", "purchases.update", "purchases.view_cost",
            "forecast.view", "reorder.view", "reorder.create_purchase_order", "reports.view"
        )));
        map.put(DomainEnums.Role.ACCOUNTANT, immutable(List.of(
            "dashboard.view", "sales.view", "purchases.view", "customers.view", "customers.view_outstanding",
            "reports.view", "reports.export", "insights.profit.view", "audit.view"
        )));
        map.put(DomainEnums.Role.VIEWER, immutable(List.of(
            "dashboard.view", "today_actions.view", "products.view", "stock.view", "reports.view",
            "forecast.view", "reorder.view", "dead_stock.view", "data_quality.view"
        )));
        map.put(DomainEnums.Role.AUDITOR, immutable(List.of(
            "dashboard.view", "today_actions.view", "products.view", "stock.view", "sales.view", "purchases.view",
            "customers.view", "customers.view_outstanding", "suppliers.view", "imports.view", "integrations.tally.view",
            "forecast.view", "reorder.view", "dead_stock.view", "data_quality.view", "insights.profit.view",
            "reports.view", "reports.export", "audit.view"
        )));
        map.put(DomainEnums.Role.CUSTOMER_USER, immutable(List.of(
            "portal.customer.view", "portal.customer.order_create", "portal.customer.invoice_view", "portal.customer.outstanding_view"
        )));
        map.put(DomainEnums.Role.SUPPLIER_USER, immutable(List.of(
            "portal.supplier.view", "portal.supplier.po_view", "portal.supplier.invoice_upload", "portal.supplier.delivery_update"
        )));
        map.put(DomainEnums.Role.PLATFORM_SUPER_ADMIN, immutable(platformPermissions));
        map.put(DomainEnums.Role.PLATFORM_SUPPORT, immutable(List.of(
            "platform.tenants.view", "platform.system_health.view", "platform.support_access.manage", "platform.audit.view"
        )));
        map.put(DomainEnums.Role.PLATFORM_BILLING_ADMIN, immutable(List.of(
            "platform.tenants.view", "platform.subscriptions.manage"
        )));
        return map;
    }

    private static Set<String> without(Set<String> source, Set<String> excluded) {
        var copy = new LinkedHashSet<>(source);
        copy.removeAll(excluded);
        return Set.copyOf(copy);
    }

    private static Set<String> immutable(Collection<String> source) {
        return Set.copyOf(new LinkedHashSet<>(source));
    }

    private record PermissionDefinition(String code, String name, String module, String description) {
        Map<String, Object> asMap() {
            var row = new LinkedHashMap<String, Object>();
            row.put("code", code);
            row.put("name", name);
            row.put("module", module);
            row.put("description", description);
            return row;
        }
    }
}
