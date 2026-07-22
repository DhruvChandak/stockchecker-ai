package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.web.dto.ApiDtos;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Service
public class TallyIntegrationService {
    private final Repositories.ImportBatchRepository batches;
    private final Repositories.ImportErrorRepository errors;
    private final Repositories.StagingProductRepository stagingProducts;
    private final Repositories.StagingCustomerRepository stagingCustomers;
    private final Repositories.StagingSupplierRepository stagingSuppliers;
    private final Repositories.StagingStockMovementRepository stagingMovements;
    private final Repositories.ReorderSuggestionRepository reorderSuggestions;
    private final Repositories.DeadStockInsightRepository deadStockInsights;
    private final DataQualityService dataQuality;

    public TallyIntegrationService(
        Repositories.ImportBatchRepository batches,
        Repositories.ImportErrorRepository errors,
        Repositories.StagingProductRepository stagingProducts,
        Repositories.StagingCustomerRepository stagingCustomers,
        Repositories.StagingSupplierRepository stagingSuppliers,
        Repositories.StagingStockMovementRepository stagingMovements,
        Repositories.ReorderSuggestionRepository reorderSuggestions,
        Repositories.DeadStockInsightRepository deadStockInsights,
        DataQualityService dataQuality
    ) {
        this.batches = batches;
        this.errors = errors;
        this.stagingProducts = stagingProducts;
        this.stagingCustomers = stagingCustomers;
        this.stagingSuppliers = stagingSuppliers;
        this.stagingMovements = stagingMovements;
        this.reorderSuggestions = reorderSuggestions;
        this.deadStockInsights = deadStockInsights;
        this.dataQuality = dataQuality;
    }

    public ApiDtos.TallyIntegrationStatus status() {
        var tenantId = TenantContext.tenantId();
        var sourceTypes = List.of(DomainEnums.SourceType.TALLY, DomainEnums.SourceType.TALLY_XML, DomainEnums.SourceType.TALLY_EXCEL, DomainEnums.SourceType.EXCEL, DomainEnums.SourceType.CSV, DomainEnums.SourceType.XML, DomainEnums.SourceType.JSON);
        var latest = batches.findByTenantIdAndSourceTypeInOrderByUpdatedAtDesc(tenantId, sourceTypes, PageRequest.of(0, 25)).getContent();
        var lastSuccess = latest.stream().filter(batch -> batch.status == DomainEnums.ImportStatus.COMMITTED).findFirst().orElse(null);
        var batchId = lastSuccess == null ? null : lastSuccess.id;
        var importErrors = latest.stream().mapToLong(batch -> batch.errorCount).sum();
        var unmapped = latest.stream()
            .flatMap(batch -> errors.findByTenantIdAndImportBatchIdOrderByRowNumberAsc(tenantId, batch.id).stream())
            .map(error -> error.fieldName)
            .filter(field -> field != null && !field.isBlank())
            .distinct()
            .limit(12)
            .toList();
        var quality = dataQuality.summary();
        var products = batchId == null ? 0 : stagingProducts.countByTenantIdAndImportBatchIdAndCommittedTrue(tenantId, batchId);
        var customers = batchId == null ? 0 : stagingCustomers.countByTenantIdAndImportBatchIdAndCommittedTrue(tenantId, batchId);
        var suppliers = batchId == null ? 0 : stagingSuppliers.countByTenantIdAndImportBatchIdAndCommittedTrue(tenantId, batchId);
        var sales = batchId == null ? 0 : stagingMovements.countByTenantIdAndImportBatchIdAndMovementTypeAndCommittedTrue(tenantId, batchId, DomainEnums.MovementType.SALE.name());
        var purchases = batchId == null ? 0 : stagingMovements.countByTenantIdAndImportBatchIdAndMovementTypeAndCommittedTrue(tenantId, batchId, DomainEnums.MovementType.PURCHASE.name());
        var insights = reorderSuggestions.findByTenantIdOrderByGeneratedAtDesc(tenantId).size() + deadStockInsights.findByTenantIdOrderByStockValueDesc(tenantId).size();
        return new ApiDtos.TallyIntegrationStatus(
            lastSuccess == null ? "NO_SUCCESSFUL_IMPORT" : "CONNECTED_BY_EXPORT",
            lastSuccess == null ? null : lastSuccess.updatedAt.atZone(ZoneOffset.UTC).toLocalDate(),
            products,
            customers,
            suppliers,
            sales,
            purchases,
            importErrors,
            unmapped,
            quality.duplicateGroups(),
            quality.productsWithMissingFields(),
            quality.qualityScore(),
            insights
        );
    }
}
