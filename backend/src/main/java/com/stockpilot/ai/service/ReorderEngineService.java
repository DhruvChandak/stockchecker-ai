package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.*;
import com.stockpilot.ai.exception.ApiErrors;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class ReorderEngineService {
    private final Repositories.ProductRepository products;
    private final Repositories.WarehouseRepository warehouses;
    private final Repositories.StockMovementRepository movements;
    private final Repositories.PurchaseOrderRepository purchaseOrders;
    private final Repositories.PurchaseOrderItemRepository purchaseOrderItems;
    private final Repositories.SalesOrderItemRepository salesOrderItems;
    private final Repositories.SupplierRepository suppliers;
    private final StockLedgerService stockLedger;
    private final AuditService audit;

    public ReorderEngineService(
        Repositories.ProductRepository products,
        Repositories.WarehouseRepository warehouses,
        Repositories.StockMovementRepository movements,
        Repositories.PurchaseOrderRepository purchaseOrders,
        Repositories.PurchaseOrderItemRepository purchaseOrderItems,
        Repositories.SalesOrderItemRepository salesOrderItems,
        Repositories.SupplierRepository suppliers,
        StockLedgerService stockLedger,
        AuditService audit
    ) {
        this.products = products;
        this.warehouses = warehouses;
        this.movements = movements;
        this.purchaseOrders = purchaseOrders;
        this.purchaseOrderItems = purchaseOrderItems;
        this.salesOrderItems = salesOrderItems;
        this.suppliers = suppliers;
        this.stockLedger = stockLedger;
        this.audit = audit;
    }

    public List<ApiDtos.SmartReorderSuggestion> suggestions() {
        var tenantId = TenantContext.tenantId();
        var rows = new ArrayList<ApiDtos.SmartReorderSuggestion>();
        for (var product : products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)) {
            for (var warehouse : warehouses.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)) {
                var current = stockLedger.currentStock(tenantId, product.id, warehouse.id);
                var last7 = demand(tenantId, product.id, warehouse.id, 7);
                var last30 = demand(tenantId, product.id, warehouse.id, 30);
                var previous30 = demandBetween(tenantId, product.id, warehouse.id, 60, 31);
                var avgDaily = demand(tenantId, product.id, warehouse.id, 90).divide(BigDecimal.valueOf(90), 3, RoundingMode.HALF_UP);
                var recentDaily = last30.divide(BigDecimal.valueOf(30), 3, RoundingMode.HALF_UP);
                var demand = recentDaily.compareTo(BigDecimal.ZERO) > 0 ? recentDaily : avgDaily;
                var pendingPo = nvl(purchaseOrderItems.pendingQuantity(tenantId, product.id));
                var pendingSo = nvl(salesOrderItems.pendingQuantity(tenantId, product.id));
                var availableAfterOrders = current.add(pendingPo).subtract(pendingSo);
                var reorderPoint = demand.multiply(BigDecimal.valueOf(product.leadTimeDays)).add(product.safetyStock == null ? BigDecimal.ZERO : product.safetyStock);
                var rawNeed = reorderPoint.add(demand.multiply(BigDecimal.valueOf(30))).subtract(availableAfterOrders);
                if (rawNeed.compareTo(BigDecimal.ZERO) <= 0 && current.compareTo(reorderPoint) > 0) {
                    continue;
                }
                var recommended = rawNeed.max(product.minimumOrderQuantity == null ? BigDecimal.ZERO : product.minimumOrderQuantity);
                LocalDate stockout = null;
                if (demand.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(BigDecimal.ZERO) > 0) {
                    stockout = LocalDate.now(ZoneOffset.UTC).plusDays(current.divide(demand, 0, RoundingMode.CEILING).longValue());
                }
                var trend = previous30.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : last30.subtract(previous30).multiply(BigDecimal.valueOf(100)).divide(previous30, 0, RoundingMode.HALF_UP);
                var reasonPrefix = trend.compareTo(BigDecimal.ZERO) > 0
                    ? "Sales increased " + trend + "% in the last 4 weeks"
                    : "Current stock plus pending purchases is near the reorder point";
                var reason = reasonPrefix
                    + ". Formula: reorder point = avg demand "
                    + demand
                    + " x lead time "
                    + product.leadTimeDays
                    + " + safety stock "
                    + (product.safetyStock == null ? BigDecimal.ZERO : product.safetyStock)
                    + "; recommended = reorder point + 30-day demand - current stock - pending purchases + pending sales, floored by MOQ.";
                rows.add(new ApiDtos.SmartReorderSuggestion(product.id, product.name, warehouse.id, warehouse.name, current, avgDaily, last7, last30, stockout, product.leadTimeDays, product.safetyStock, product.minimumOrderQuantity, pendingPo, pendingSo, recommended.setScale(3, RoundingMode.HALF_UP), reason));
            }
        }
        rows.sort(Comparator.comparing(ApiDtos.SmartReorderSuggestion::expectedStockoutDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    @Transactional
    public ApiDtos.DraftPurchaseOrderResponse createDraftPurchaseOrder(ApiDtos.DraftPurchaseOrderRequest request) {
        var tenantId = TenantContext.tenantId();
        var candidates = suggestions().stream()
            .filter(row -> request.productIds() == null || request.productIds().isEmpty() || request.productIds().contains(row.productId()))
            .filter(row -> row.recommendedQuantity().compareTo(BigDecimal.ZERO) > 0)
            .toList();
        if (candidates.isEmpty()) {
            throw ApiErrors.badRequest("No reorder suggestions are available for a draft purchase order");
        }
        var supplierId = request.supplierId();
        if (supplierId == null) {
            supplierId = suppliers.findByTenantIdOrderByNameAsc(tenantId).stream().findFirst().map(s -> s.id).orElse(null);
        } else {
            suppliers.findByTenantIdAndId(tenantId, supplierId).orElseThrow(() -> ApiErrors.notFound("Supplier not found"));
        }
        var order = new PurchaseOrder();
        order.tenantId = tenantId;
        order.supplierId = supplierId;
        order.status = "DRAFT";
        order.orderNumber = "DRAFT-PO-" + LocalDate.now(ZoneOffset.UTC) + "-" + System.currentTimeMillis();
        purchaseOrders.save(order);
        BigDecimal total = BigDecimal.ZERO;
        for (var row : candidates) {
            var product = products.findByTenantIdAndId(tenantId, row.productId()).orElseThrow();
            var item = new PurchaseOrderItem();
            item.tenantId = tenantId;
            item.purchaseOrderId = order.id;
            item.productId = product.id;
            item.warehouseId = row.warehouseId();
            item.quantity = row.recommendedQuantity();
            item.unitId = product.baseUnitId;
            item.rate = product.defaultPurchasePrice == null ? BigDecimal.ZERO : product.defaultPurchasePrice;
            item.lineTotal = item.quantity.multiply(item.rate);
            purchaseOrderItems.save(item);
            total = total.add(item.lineTotal);
        }
        order.totalAmount = total;
        purchaseOrders.save(order);
        audit.logCurrent("DRAFT_PURCHASE_ORDER_CREATED", "PurchaseOrder", order.id, Map.of("itemCount", candidates.size(), "total", total));
        return new ApiDtos.DraftPurchaseOrderResponse(order.id, order.orderNumber, order.totalAmount, candidates.size());
    }

    private BigDecimal demand(UUID tenantId, UUID productId, UUID warehouseId, int days) {
        return demandBetween(tenantId, productId, warehouseId, days, 1);
    }

    private BigDecimal demandBetween(UUID tenantId, UUID productId, UUID warehouseId, int fromDaysAgo, int toDaysAgo) {
        var now = Instant.now();
        var from = now.minus(Duration.ofDays(fromDaysAgo));
        var to = now.minus(Duration.ofDays(toDaysAgo));
        return movements.findByTenantIdAndMovementTypeAndMovementDateAfter(tenantId, DomainEnums.MovementType.SALE, from).stream()
            .filter(movement -> movement.productId.equals(productId))
            .filter(movement -> warehouseId == null || warehouseId.equals(movement.warehouseId))
            .filter(movement -> !movement.movementDate.isAfter(to))
            .map(movement -> movement.baseQuantity.abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
