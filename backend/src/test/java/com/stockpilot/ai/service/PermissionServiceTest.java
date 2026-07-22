package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.DomainEnums;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceTest {
    private final PermissionService permissions = new PermissionService(null, null);

    @Test
    void warehouseStaffCanAdjustStockButCannotViewProfit() {
        var granted = permissions.permissionsFor(DomainEnums.Role.WAREHOUSE_STAFF);

        assertThat(granted).contains("stock.view", "stock.adjust", "stock.transfer");
        assertThat(granted).doesNotContain("insights.profit.view", "reports.export", "purchases.view_cost");
    }

    @Test
    void salesStaffCannotViewPurchaseCostOrProfitMarginByDefault() {
        var granted = permissions.permissionsFor(DomainEnums.Role.SALES_STAFF);

        assertThat(granted).contains("sales.create", "customers.view_outstanding");
        assertThat(granted).doesNotContain("purchases.view_cost", "sales.view_margin", "reports.export");
    }

    @Test
    void viewerIsReadOnlyAndCannotExportReports() {
        var granted = permissions.permissionsFor(DomainEnums.Role.VIEWER);

        assertThat(granted).contains("dashboard.view", "products.view", "stock.view", "reports.view");
        assertThat(granted).doesNotContain("products.create", "stock.adjust", "imports.commit", "reports.export");
    }

    @Test
    void purchaseManagerCanGenerateReorderPurchaseOrders() {
        var granted = permissions.permissionsFor(DomainEnums.Role.PURCHASE_MANAGER);

        assertThat(granted).contains("purchases.create", "reorder.view", "reorder.create_purchase_order");
        assertThat(granted).doesNotContain("sales.create", "billing.manage");
    }

    @Test
    void customerUserOnlyReceivesCustomerPortalPermissions() {
        var granted = permissions.permissionsFor(DomainEnums.Role.CUSTOMER_USER);

        assertThat(granted).contains("portal.customer.view", "portal.customer.order_create", "portal.customer.invoice_view", "portal.customer.outstanding_view");
        assertThat(granted).doesNotContain("dashboard.view", "customers.view", "stock.view");
    }
}
