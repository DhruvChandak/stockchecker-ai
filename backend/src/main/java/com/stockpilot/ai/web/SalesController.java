package com.stockpilot.ai.web;

import com.stockpilot.ai.service.SalesService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
public class SalesController {
    private final SalesService sales;

    public SalesController(SalesService sales) {
        this.sales = sales;
    }

    @GetMapping
    @PreAuthorize("@permissionService.has('sales.view')")
    public Page<ApiDtos.InvoiceResponse> list(Pageable pageable) {
        return sales.list(pageable);
    }

    @PostMapping
    @PreAuthorize("@permissionService.has('sales.create')")
    public ApiDtos.InvoiceResponse create(@Valid @RequestBody ApiDtos.SalesInvoiceRequest request) {
        return sales.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.has('sales.view')")
    public ApiDtos.InvoiceResponse get(@PathVariable UUID id) {
        return sales.get(id);
    }
}
