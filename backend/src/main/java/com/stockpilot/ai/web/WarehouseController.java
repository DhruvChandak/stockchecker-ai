package com.stockpilot.ai.web;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.Warehouse;
import com.stockpilot.ai.exception.ApiErrors;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final Repositories.WarehouseRepository warehouses;

    public WarehouseController(Repositories.WarehouseRepository warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping
    @PreAuthorize("@permissionService.has('stock.view')")
    public List<ApiDtos.WarehouseResponse> list() {
        return warehouses.findByTenantIdAndActiveTrueOrderByNameAsc(TenantContext.tenantId()).stream().map(this::response).toList();
    }

    @PostMapping
    @PreAuthorize("@permissionService.has('settings.manage')")
    public ApiDtos.WarehouseResponse create(@Valid @RequestBody ApiDtos.WarehouseRequest request) {
        var warehouse = new Warehouse();
        warehouse.tenantId = TenantContext.tenantId();
        apply(warehouse, request);
        return response(warehouses.save(warehouse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.has('settings.manage')")
    public ApiDtos.WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody ApiDtos.WarehouseRequest request) {
        var warehouse = warehouses.findByTenantIdAndId(TenantContext.tenantId(), id).orElseThrow(() -> ApiErrors.notFound("Warehouse not found"));
        apply(warehouse, request);
        return response(warehouses.save(warehouse));
    }

    private void apply(Warehouse warehouse, ApiDtos.WarehouseRequest request) {
        warehouse.name = request.name();
        warehouse.code = request.code();
        warehouse.address = request.address();
    }

    private ApiDtos.WarehouseResponse response(Warehouse warehouse) {
        return new ApiDtos.WarehouseResponse(warehouse.id, warehouse.name, warehouse.code, warehouse.address, warehouse.active);
    }
}
