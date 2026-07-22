package com.stockpilot.ai.web;

import com.stockpilot.ai.service.DashboardService;
import com.stockpilot.ai.service.OwnerActionService;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private final OwnerActionService actions;

    public DashboardController(DashboardService dashboard, OwnerActionService actions) {
        this.dashboard = dashboard;
        this.actions = actions;
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public ApiDtos.DashboardSummary summary() {
        return dashboard.summary();
    }

    @GetMapping("/sales-trend")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public List<ApiDtos.TrendPoint> salesTrend() {
        return dashboard.salesTrend();
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public List<ApiDtos.TrendPoint> profitLoss() {
        return dashboard.profitTrend();
    }

    @GetMapping("/low-stock")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public List<ApiDtos.LowStockResponse> lowStock() {
        return dashboard.lowStock();
    }

    @GetMapping("/dead-stock")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public List<ApiDtos.DeadStockResponse> deadStock() {
        return dashboard.deadStock();
    }

    @GetMapping("/outstanding")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public Map<String, Object> outstanding() {
        return Map.of("outstandingReceivables", dashboard.outstandingReceivables());
    }

    @GetMapping("/actions")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public List<ApiDtos.ActionCard> actions() {
        return actions.today();
    }

    @GetMapping("/top-products")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public List<ApiDtos.DashboardProductMetric> topProducts() {
        return dashboard.topProducts();
    }

    @GetMapping("/slow-moving-products")
    @PreAuthorize("@permissionService.has('dashboard.view')")
    public List<ApiDtos.DashboardProductMetric> slowMovingProducts() {
        return dashboard.slowMovingProducts();
    }
}
