package com.stockpilot.ai.service;

import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.domain.BaseAudit;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class SmartImportFingerprintService {
    private final Repositories.ImportSessionFileRepository files;
    private final Repositories.ImportPlanIssueRepository issues;
    private final Repositories.SmartStagedProductRepository stagedProducts;
    private final Repositories.SmartStagedPartyRepository stagedParties;
    private final Repositories.SmartStagedWarehouseRepository stagedWarehouses;
    private final Repositories.SmartStagedUnitRepository stagedUnits;
    private final Repositories.SmartStagedStockSnapshotRepository stagedSnapshots;
    private final Repositories.SmartStagedVoucherRepository stagedVouchers;
    private final Repositories.SmartStagedVoucherItemRepository stagedVoucherItems;
    private final Repositories.SmartStagedCashbookEntryRepository stagedCashbook;
    private final Repositories.SmartStagedStockAgeingRepository stagedAgeing;

    public SmartImportFingerprintService(
        Repositories.ImportSessionFileRepository files,
        Repositories.ImportPlanIssueRepository issues,
        Repositories.SmartStagedProductRepository stagedProducts,
        Repositories.SmartStagedPartyRepository stagedParties,
        Repositories.SmartStagedWarehouseRepository stagedWarehouses,
        Repositories.SmartStagedUnitRepository stagedUnits,
        Repositories.SmartStagedStockSnapshotRepository stagedSnapshots,
        Repositories.SmartStagedVoucherRepository stagedVouchers,
        Repositories.SmartStagedVoucherItemRepository stagedVoucherItems,
        Repositories.SmartStagedCashbookEntryRepository stagedCashbook,
        Repositories.SmartStagedStockAgeingRepository stagedAgeing
    ) {
        this.files = files;
        this.issues = issues;
        this.stagedProducts = stagedProducts;
        this.stagedParties = stagedParties;
        this.stagedWarehouses = stagedWarehouses;
        this.stagedUnits = stagedUnits;
        this.stagedSnapshots = stagedSnapshots;
        this.stagedVouchers = stagedVouchers;
        this.stagedVoucherItems = stagedVoucherItems;
        this.stagedCashbook = stagedCashbook;
        this.stagedAgeing = stagedAgeing;
    }

    public String capture(UUID tenantId, UUID sessionId) {
        var canonical = new StringBuilder();
        files.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(tenantId, sessionId).forEach(file -> canonical
            .append("FILE|").append(file.id).append('|').append(safe(file.fileHash)).append('|')
            .append(file.detectedFileType).append('|').append(file.selectedFileType).append('|')
            .append(file.status).append('|').append(file.rowCount).append('|')
            .append(file.errorCount).append('|').append(file.warningCount).append('|')
            .append(file.updatedAt).append('\n'));
        issues.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(tenantId, sessionId).forEach(issue -> canonical
            .append("ISSUE|").append(issue.id).append('|').append(issue.severity).append('|')
            .append(safe(issue.code)).append('|').append(issue.resolved).append('|')
            .append(issue.resolutionJson).append('|').append(issue.updatedAt).append('\n'));
        append(canonical, "PRODUCT", stagedProducts.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "PARTY", stagedParties.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "WAREHOUSE", stagedWarehouses.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "UNIT", stagedUnits.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "SNAPSHOT", stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "VOUCHER", stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "VOUCHER_ITEM", stagedVoucherItems.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "CASHBOOK", stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(tenantId, sessionId));
        append(canonical, "AGEING", stagedAgeing.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, sessionId));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void append(StringBuilder canonical, String type, Iterable<? extends BaseAudit> rows) {
        for (var row : rows) {
            canonical.append("STAGED|").append(type).append('|').append(row.id).append('|')
                .append(row.updatedAt).append('\n');
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
