package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.exception.ApiErrors;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DevToolsService {
    private final EntityManager entityManager;
    private final AuditService auditService;

    public DevToolsService(EntityManager entityManager, AuditService auditService) {
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> resetCurrentTenantBusinessData(String confirmation) {
        if (!"RESET WORKSPACE".equals(confirmation)) {
            throw ApiErrors.badRequest("Typed confirmation must be RESET WORKSPACE");
        }
        var tenantId = TenantContext.tenantId();
        var deleted = new LinkedHashMap<String, Integer>();
        for (var table : resetOrder()) {
            deleted.put(table, deleteTenantRows(table));
        }
        auditService.logCurrent("DEV_WORKSPACE_RESET", "Tenant", tenantId, Map.of("deleted", deleted));
        return Map.of("tenantId", tenantId, "deleted", deleted);
    }

    private List<String> resetOrder() {
        return List.of(
            "ai_messages where conversation_id in (select id from ai_conversations where tenant_id = :tenantId)",
            "sales_order_items",
            "purchase_order_items",
            "sales_invoice_items",
            "purchase_invoice_items",
            "delivery_challans",
            "customer_payments",
            "supplier_payments",
            "stock_movements",
            "forecast_results",
            "reorder_suggestions",
            "dead_stock_insights",
            "profit_insights",
            "ai_conversations",
            "payment_reminders",
            "import_effects",
            "import_errors",
            "staging_stock_movements",
            "staging_invoices",
            "staging_suppliers",
            "staging_customers",
            "staging_products",
            "import_files",
            "import_mapping_templates",
            "import_batches",
            "sales_orders",
            "purchase_orders",
            "sales_invoices",
            "purchase_invoices",
            "customer_price_lists",
            "product_barcodes",
            "product_tax_infos",
            "product_batches",
            "reorder_settings",
            "unit_conversions",
            "products",
            "customers",
            "suppliers",
            "warehouses",
            "branches",
            "brands",
            "product_categories",
            "units_of_measure"
        );
    }

    private int deleteTenantRows(String tableExpression) {
        var sql = tableExpression.contains(" where ")
            ? "delete from " + tableExpression
            : "delete from " + tableExpression + " where tenant_id = :tenantId";
        return entityManager.createNativeQuery(sql)
            .setParameter("tenantId", TenantContext.tenantId())
            .executeUpdate();
    }
}
