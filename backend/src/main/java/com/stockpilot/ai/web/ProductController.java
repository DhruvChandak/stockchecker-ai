package com.stockpilot.ai.web;

import com.stockpilot.ai.service.CatalogService;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize("@permissionService.has('products.view')")
    public Page<ApiDtos.ProductResponse> list(@RequestParam(required = false) String query, Pageable pageable) {
        return catalog.list(query, pageable);
    }

    @PostMapping
    @PreAuthorize("@permissionService.has('products.create')")
    public ApiDtos.ProductResponse create(@Valid @RequestBody ApiDtos.ProductRequest request) {
        return catalog.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.has('products.view')")
    public ApiDtos.ProductResponse get(@PathVariable UUID id) {
        return catalog.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.has('products.update')")
    public ApiDtos.ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ApiDtos.ProductRequest request) {
        return catalog.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.has('products.delete')")
    public void delete(@PathVariable UUID id) {
        catalog.delete(id);
    }

    @GetMapping("/search")
    @PreAuthorize("@permissionService.has('products.view')")
    public Page<ApiDtos.ProductResponse> search(@RequestParam String query, Pageable pageable) {
        return catalog.list(query, pageable);
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("@permissionService.has('products.view')")
    public ApiDtos.ProductResponse barcode(@PathVariable String barcode) {
        return catalog.byBarcode(barcode);
    }
}
