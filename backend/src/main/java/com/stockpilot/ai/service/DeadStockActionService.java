package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.exception.ApiErrors;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class DeadStockActionService {
    private final Repositories.ProductRepository products;
    private final Repositories.WarehouseRepository warehouses;
    private final Repositories.StockMovementRepository movements;
    private final StockLedgerService stockLedger;
    private final AuditService audit;

    public DeadStockActionService(
        Repositories.ProductRepository products,
        Repositories.WarehouseRepository warehouses,
        Repositories.StockMovementRepository movements,
        StockLedgerService stockLedger,
        AuditService audit
    ) {
        this.products = products;
        this.warehouses = warehouses;
        this.movements = movements;
        this.stockLedger = stockLedger;
        this.audit = audit;
    }

    public List<ApiDtos.DeadStockActionResponse> list() {
        var tenantId = TenantContext.tenantId();
        var today = LocalDate.now(ZoneOffset.UTC);
        var rows = new ArrayList<ApiDtos.DeadStockActionResponse>();
        for (var product : products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)) {
            for (var warehouse : warehouses.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)) {
                var stock = stockLedger.currentStock(tenantId, product.id, warehouse.id);
                if (stock.compareTo(BigDecimal.ZERO) <= 0) continue;
                var productMovements = movements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, product.id);
                var lastSale = productMovements.stream()
                    .filter(movement -> movement.movementType == DomainEnums.MovementType.SALE)
                    .filter(movement -> warehouse.id.equals(movement.warehouseId))
                    .findFirst()
                    .map(movement -> movement.movementDate.atZone(ZoneOffset.UTC).toLocalDate())
                    .orElse(null);
                var daysSince = lastSale == null ? 9999 : java.time.temporal.ChronoUnit.DAYS.between(lastSale, today);
                if (daysSince < 90) continue;
                var sold90 = productMovements.stream()
                    .filter(movement -> movement.movementType == DomainEnums.MovementType.SALE)
                    .filter(movement -> warehouse.id.equals(movement.warehouseId))
                    .filter(movement -> !movement.movementDate.isBefore(today.minusDays(90).atStartOfDay().toInstant(ZoneOffset.UTC)))
                    .map(movement -> movement.baseQuantity.abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                var avgMonthly = sold90.divide(BigDecimal.valueOf(3), 3, RoundingMode.HALF_UP);
                var value = stock.multiply(product.defaultPurchasePrice == null ? BigDecimal.ZERO : product.defaultPurchasePrice);
                var action = actionFor(daysSince, avgMonthly);
                rows.add(new ApiDtos.DeadStockActionResponse(product.id, product.name, warehouse.name, stock, value, lastSale, daysSince, avgMonthly, value, action));
            }
        }
        rows.sort(Comparator.comparing(ApiDtos.DeadStockActionResponse::blockedCapital).reversed());
        return rows;
    }

    @Transactional
    public Map<String, Object> recordAction(UUID productId, ApiDtos.DeadStockActionRequest request) {
        var tenantId = TenantContext.tenantId();
        var product = products.findByTenantIdAndId(tenantId, productId).orElseThrow(() -> ApiErrors.notFound("Product not found"));
        var allowed = Set.of("apply discount", "bundle with fast-moving item", "transfer to another warehouse", "stop reordering", "return to supplier", "mark as clearance item");
        if (!allowed.contains(request.action().toLowerCase(Locale.ROOT))) {
            throw ApiErrors.badRequest("Unsupported dead-stock action");
        }
        audit.logCurrent("DEAD_STOCK_ACTION", "Product", product.id, Map.of("action", request.action(), "notes", request.notes() == null ? "" : request.notes()));
        return Map.of("productId", product.id, "action", request.action(), "status", "RECORDED");
    }

    private String actionFor(long daysSince, BigDecimal avgMonthly) {
        if (daysSince > 180) return "mark as clearance item";
        if (avgMonthly.compareTo(BigDecimal.ONE) < 0) return "bundle with fast-moving item";
        return "apply discount";
    }
}
