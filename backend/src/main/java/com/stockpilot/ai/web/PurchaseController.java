package com.stockpilot.ai.web;

import com.stockpilot.ai.service.PurchaseService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final PurchaseService purchases;

    public PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    @PreAuthorize("@permissionService.has('purchases.view')")
    public Page<ApiDtos.InvoiceResponse> list(Pageable pageable) {
        return purchases.list(pageable);
    }

    @PostMapping
    @PreAuthorize("@permissionService.has('purchases.create')")
    public ApiDtos.InvoiceResponse create(@Valid @RequestBody ApiDtos.PurchaseInvoiceRequest request) {
        return purchases.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.has('purchases.view')")
    public ApiDtos.InvoiceResponse get(@PathVariable UUID id) {
        return purchases.get(id);
    }
}
