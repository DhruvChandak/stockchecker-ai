package com.stockpilot.ai.web;

import com.stockpilot.ai.service.ReorderEngineService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reorder")
public class ReorderController {
    private final ReorderEngineService reorder;

    public ReorderController(ReorderEngineService reorder) {
        this.reorder = reorder;
    }

    @GetMapping("/suggestions")
    @PreAuthorize("@permissionService.has('reorder.view')")
    public List<ApiDtos.SmartReorderSuggestion> suggestions() {
        return reorder.suggestions();
    }

    @PostMapping("/generate-purchase-order")
    @PreAuthorize("@permissionService.has('reorder.create_purchase_order')")
    public ApiDtos.DraftPurchaseOrderResponse generatePurchaseOrder(@Valid @RequestBody ApiDtos.DraftPurchaseOrderRequest request) {
        return reorder.createDraftPurchaseOrder(request);
    }
}
