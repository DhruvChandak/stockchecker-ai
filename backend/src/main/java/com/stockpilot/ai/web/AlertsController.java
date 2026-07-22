package com.stockpilot.ai.web;

import com.stockpilot.ai.service.DashboardService;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertsController {
    private final DashboardService dashboard;

    public AlertsController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/low-stock")
    @PreAuthorize("@permissionService.has('dashboard.view') or @permissionService.has('stock.view')")
    public List<ApiDtos.LowStockResponse> lowStock() {
        return dashboard.lowStock();
    }
}
