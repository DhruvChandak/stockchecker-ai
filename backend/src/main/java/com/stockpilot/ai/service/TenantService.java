package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.Tenant;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.exception.ApiErrors;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class TenantService {
    private final Repositories.TenantRepository tenants;
    private final AuditService auditService;

    public TenantService(Repositories.TenantRepository tenants, AuditService auditService) {
        this.tenants = tenants;
        this.auditService = auditService;
    }

    public Tenant current() {
        return tenants.findByIdAndStatus(TenantContext.tenantId(), DomainEnums.TenantStatus.ACTIVE)
            .orElseThrow(() -> ApiErrors.notFound("Active workspace not found"));
    }

    public ApiDtos.TenantResponse response(Tenant tenant) {
        return new ApiDtos.TenantResponse(tenant.id, tenant.name, tenant.businessMode, tenant.currency, tenant.gstEnabled, tenant.allowNegativeStock, tenant.portalShowAllActiveProducts);
    }

    @Transactional
    public ApiDtos.TenantResponse update(ApiDtos.TenantUpdateRequest request) {
        var tenant = current();
        tenant.name = request.name();
        tenant.currency = request.currency();
        tenant.gstEnabled = request.gstEnabled();
        tenant.allowNegativeStock = request.allowNegativeStock();
        tenant.portalShowAllActiveProducts = request.portalShowAllActiveProducts();
        auditService.logCurrent("BUSINESS_SETTINGS_UPDATED", "Tenant", tenant.id, Map.of(
            "name", tenant.name,
            "allowNegativeStock", tenant.allowNegativeStock,
            "portalShowAllActiveProducts", tenant.portalShowAllActiveProducts
        ));
        return response(tenants.save(tenant));
    }

    @Transactional
    public ApiDtos.TenantResponse updateMode(ApiDtos.BusinessModeRequest request) {
        var tenant = current();
        tenant.businessMode = request.businessMode();
        auditService.logCurrent("BUSINESS_MODE_UPDATED", "Tenant", tenant.id, Map.of("businessMode", tenant.businessMode.name()));
        return response(tenants.save(tenant));
    }
}
