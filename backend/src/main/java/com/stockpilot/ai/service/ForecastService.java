package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.domain.ForecastResult;
import com.stockpilot.ai.domain.ReorderSuggestion;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class ForecastService {
    private final Repositories.ProductRepository products;
    private final Repositories.StockMovementRepository movements;
    private final Repositories.ForecastResultRepository forecasts;
    private final Repositories.ReorderSuggestionRepository suggestions;
    private final StockLedgerService stockLedger;

    public ForecastService(
        Repositories.ProductRepository products,
        Repositories.StockMovementRepository movements,
        Repositories.ForecastResultRepository forecasts,
        Repositories.ReorderSuggestionRepository suggestions,
        StockLedgerService stockLedger
    ) {
        this.products = products;
        this.movements = movements;
        this.forecasts = forecasts;
        this.suggestions = suggestions;
        this.stockLedger = stockLedger;
    }

    @Transactional
    public List<ApiDtos.ForecastResponse> run() {
        var tenantId = TenantContext.tenantId();
        forecasts.deleteByTenantId(tenantId);
        suggestions.deleteByTenantId(tenantId);
        var since = LocalDate.now(ZoneOffset.UTC).minusDays(89);
        var sales = movements.findByTenantIdAndMovementTypeAndMovementDateAfter(tenantId, DomainEnums.MovementType.SALE, since.atStartOfDay().toInstant(ZoneOffset.UTC));
        var dailyByProduct = new LinkedHashMap<UUID, List<BigDecimal>>();
        for (var product : products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)) {
            var days = new ArrayList<BigDecimal>();
            for (int i = 0; i < 90; i++) {
                days.add(BigDecimal.ZERO);
            }
            dailyByProduct.put(product.id, days);
        }
        for (var sale : sales) {
            var date = LocalDateTime.ofInstant(sale.movementDate, ZoneOffset.UTC).toLocalDate();
            var index = (int) Duration.between(since.atStartOfDay(), date.atStartOfDay()).toDays();
            if (index >= 0 && index < 90 && dailyByProduct.containsKey(sale.productId)) {
                var days = dailyByProduct.get(sale.productId);
                days.set(index, days.get(index).add(sale.baseQuantity.abs()));
            }
        }

        var responses = new ArrayList<ApiDtos.ForecastResponse>();
        for (var product : products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)) {
            var daily = dailyByProduct.getOrDefault(product.id, List.of());
            var avg = ForecastMath.averageDailyDemand(daily);
            var weighted = ForecastMath.weightedDailyDemand(daily);
            var demand = weighted.compareTo(BigDecimal.ZERO) > 0 ? weighted : avg;
            var current = stockLedger.currentStock(tenantId, product.id, null);
            var next7 = demand.multiply(BigDecimal.valueOf(7)).setScale(3, java.math.RoundingMode.HALF_UP);
            var next30 = demand.multiply(BigDecimal.valueOf(30)).setScale(3, java.math.RoundingMode.HALF_UP);
            LocalDate stockout = null;
            if (avg.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(BigDecimal.ZERO) > 0) {
                var daysToStockout = current.divide(avg, 0, java.math.RoundingMode.CEILING).longValue();
                stockout = LocalDate.now(ZoneOffset.UTC).plusDays(daysToStockout);
            }
            var forecast = new ForecastResult();
            forecast.tenantId = tenantId;
            forecast.productId = product.id;
            forecast.averageDailyDemand = avg;
            forecast.weightedDailyDemand = weighted;
            forecast.next7DaysDemand = next7;
            forecast.next30DaysDemand = next30;
            forecast.currentStock = current;
            forecast.stockoutDate = stockout;
            forecast.generatedAt = Instant.now();
            forecasts.save(forecast);

            var reorderPoint = ForecastMath.reorderPoint(demand, product.leadTimeDays, product.safetyStock);
            var suggested = ForecastMath.suggestedQuantity(reorderPoint, next30, current, product.minimumOrderQuantity);
            if (current.compareTo(reorderPoint) <= 0 || suggested.compareTo(BigDecimal.ZERO) > 0) {
                var suggestion = new ReorderSuggestion();
                suggestion.tenantId = tenantId;
                suggestion.productId = product.id;
                suggestion.reorderPoint = reorderPoint;
                suggestion.suggestedQuantity = suggested;
                suggestion.reason = "Demand forecast uses last 90 days sales. Current stock " + current + ", reorder point " + reorderPoint + ".";
                suggestion.generatedAt = Instant.now();
                suggestions.save(suggestion);
            }
            responses.add(new ApiDtos.ForecastResponse(product.id, product.name, current, avg, next7, next30, stockout));
        }
        return responses;
    }

    public List<ApiDtos.ForecastResponse> results() {
        var tenantId = TenantContext.tenantId();
        return forecasts.findByTenantIdOrderByGeneratedAtDesc(tenantId).stream()
            .map(result -> {
                var product = products.findByTenantIdAndId(tenantId, result.productId).orElse(null);
                return new ApiDtos.ForecastResponse(result.productId, product == null ? "Unknown product" : product.name, result.currentStock, result.averageDailyDemand, result.next7DaysDemand, result.next30DaysDemand, result.stockoutDate);
            })
            .toList();
    }

    public List<ApiDtos.ReorderSuggestionResponse> suggestions() {
        var tenantId = TenantContext.tenantId();
        return suggestions.findByTenantIdOrderByGeneratedAtDesc(tenantId).stream()
            .map(suggestion -> {
                var product = products.findByTenantIdAndId(tenantId, suggestion.productId).orElse(null);
                return new ApiDtos.ReorderSuggestionResponse(suggestion.productId, product == null ? "Unknown product" : product.name, suggestion.reorderPoint, suggestion.suggestedQuantity, suggestion.reason, suggestion.status);
            })
            .toList();
    }
}
