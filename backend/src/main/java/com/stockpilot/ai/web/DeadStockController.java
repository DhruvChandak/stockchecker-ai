package com.stockpilot.ai.web;

import com.stockpilot.ai.service.DeadStockActionService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dead-stock")
public class DeadStockController {
    private final DeadStockActionService deadStock;

    public DeadStockController(DeadStockActionService deadStock) {
        this.deadStock = deadStock;
    }

    @GetMapping
    @PreAuthorize("@permissionService.has('dead_stock.view')")
    public List<ApiDtos.DeadStockActionResponse> list() {
        return deadStock.list();
    }

    @PostMapping("/{productId}/action")
    @PreAuthorize("@permissionService.has('dead_stock.action')")
    public Map<String, Object> action(@PathVariable UUID productId, @Valid @RequestBody ApiDtos.DeadStockActionRequest request) {
        return deadStock.recordAction(productId, request);
    }
}
