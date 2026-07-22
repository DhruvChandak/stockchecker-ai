package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.*;
import com.stockpilot.ai.exception.ApiErrors;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.web.dto.SmartImportDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SmartImportDryRunService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final List<String> SUMMARY_KEYS = List.of(
        "filesProcessed", "duplicateFilesSkipped", "unknownFiles",
        "productsToCreate", "productsMatchedExisting", "possibleDuplicateProducts", "productsReviewRequired",
        "customersToCreate", "suppliersToCreate", "partiesMatchedExisting", "partiesReviewRequired",
        "warehousesToCreate", "warehousesMatchedExisting", "warehousesReviewRequired",
        "stockSnapshotsProcessed", "snapshotAdjustmentsPreviewed", "snapshotNoChangeRows",
        "stockMovementsPreviewed", "stockMovementsSkippedDueToInvoiceOnly", "stockMovementsSkippedBeforeSnapshotDate",
        "negativeStockRowsFound", "negativeStockRowsSkipped", "negativeStockRowsBlocked",
        "purchaseInvoicesPreviewed", "salesInvoicesPreviewed", "creditNotesPreviewed", "debitNotesPreviewed",
        "voucherItemsReady", "vouchersBlocked", "duplicateVouchersSkipped",
        "debtorRowsProcessed", "creditorRowsProcessed", "outstandingSnapshotsPreviewed",
        "cashbookRowsReady", "cashbookRowsMatched", "cashbookRowsUnmatched", "cashbookRowsReviewRequired",
        "customerPaymentsPreviewed", "supplierPaymentsPreviewed", "paymentsLinkedToSalesInvoices",
        "paymentsLinkedToPurchaseInvoices", "duplicatePaymentsSkipped",
        "ageingRowsMatched", "ageingRowsUnmatched", "ratesDerived", "zeroRateItems", "zeroCostItems",
        "ratesSignNormalized", "ledgerLinesSkipped", "invalidRateRows",
        "taxLinesCaptured", "discountLinesCaptured", "freightLinesCaptured", "roundOffLinesCaptured", "otherChargeLinesCaptured",
        "warnings", "blockingErrors", "reviewRequiredCount"
    );

    private final Repositories.ImportSessionRepository sessions;
    private final Repositories.ImportSessionFileRepository files;
    private final Repositories.ImportPlanRepository plans;
    private final Repositories.ImportPlanIssueRepository planIssues;
    private final Repositories.ImportDryRunRepository dryRuns;
    private final Repositories.ImportDryRunItemRepository dryRunItems;
    private final Repositories.SmartStagedProductRepository stagedProducts;
    private final Repositories.SmartStagedPartyRepository stagedParties;
    private final Repositories.SmartStagedWarehouseRepository stagedWarehouses;
    private final Repositories.SmartStagedUnitRepository stagedUnits;
    private final Repositories.SmartStagedStockSnapshotRepository stagedSnapshots;
    private final Repositories.SmartStagedVoucherRepository stagedVouchers;
    private final Repositories.SmartStagedVoucherItemRepository stagedVoucherItems;
    private final Repositories.SmartStagedCashbookEntryRepository stagedCashbook;
    private final Repositories.SmartStagedStockAgeingRepository stagedAgeing;
    private final Repositories.WarehouseRepository warehouses;
    private final Repositories.SalesInvoiceRepository salesInvoices;
    private final Repositories.PurchaseInvoiceRepository purchaseInvoices;
    private final Repositories.FinancialAdjustmentRepository financialAdjustments;
    private final Repositories.CustomerPaymentRepository customerPayments;
    private final Repositories.SupplierPaymentRepository supplierPayments;
    private final StockLedgerService stockLedger;
    private final AuditService audit;
    private final SmartImportFingerprintService fingerprints;

    public SmartImportDryRunService(
        Repositories.ImportSessionRepository sessions,
        Repositories.ImportSessionFileRepository files,
        Repositories.ImportPlanRepository plans,
        Repositories.ImportPlanIssueRepository planIssues,
        Repositories.ImportDryRunRepository dryRuns,
        Repositories.ImportDryRunItemRepository dryRunItems,
        Repositories.SmartStagedProductRepository stagedProducts,
        Repositories.SmartStagedPartyRepository stagedParties,
        Repositories.SmartStagedWarehouseRepository stagedWarehouses,
        Repositories.SmartStagedUnitRepository stagedUnits,
        Repositories.SmartStagedStockSnapshotRepository stagedSnapshots,
        Repositories.SmartStagedVoucherRepository stagedVouchers,
        Repositories.SmartStagedVoucherItemRepository stagedVoucherItems,
        Repositories.SmartStagedCashbookEntryRepository stagedCashbook,
        Repositories.SmartStagedStockAgeingRepository stagedAgeing,
        Repositories.WarehouseRepository warehouses,
        Repositories.SalesInvoiceRepository salesInvoices,
        Repositories.PurchaseInvoiceRepository purchaseInvoices,
        Repositories.FinancialAdjustmentRepository financialAdjustments,
        Repositories.CustomerPaymentRepository customerPayments,
        Repositories.SupplierPaymentRepository supplierPayments,
        StockLedgerService stockLedger,
        AuditService audit,
        SmartImportFingerprintService fingerprints
    ) {
        this.sessions = sessions;
        this.files = files;
        this.plans = plans;
        this.planIssues = planIssues;
        this.dryRuns = dryRuns;
        this.dryRunItems = dryRunItems;
        this.stagedProducts = stagedProducts;
        this.stagedParties = stagedParties;
        this.stagedWarehouses = stagedWarehouses;
        this.stagedUnits = stagedUnits;
        this.stagedSnapshots = stagedSnapshots;
        this.stagedVouchers = stagedVouchers;
        this.stagedVoucherItems = stagedVoucherItems;
        this.stagedCashbook = stagedCashbook;
        this.stagedAgeing = stagedAgeing;
        this.warehouses = warehouses;
        this.salesInvoices = salesInvoices;
        this.purchaseInvoices = purchaseInvoices;
        this.financialAdjustments = financialAdjustments;
        this.customerPayments = customerPayments;
        this.supplierPayments = supplierPayments;
        this.stockLedger = stockLedger;
        this.audit = audit;
        this.fingerprints = fingerprints;
    }

    @Transactional
    public Map<String, Object> run(UUID sessionId, SmartImportDtos.DryRunRequest request) {
        var session = session(sessionId);
        var plan = plans.findByTenantIdAndImportSessionId(session.tenantId, session.id)
            .orElseThrow(() -> ApiErrors.badRequest("Stage the Smart Import session before running a dry run"));
        if (!Boolean.TRUE.equals(plan.planJson.get("stagingComplete"))) {
            throw ApiErrors.badRequest("Stage the Smart Import session before running a dry run");
        }
        var strategy = request != null && request.strategy() != null ? request.strategy() : session.recommendedStrategy;
        if (strategy == null || strategy == DomainEnums.ImportStrategy.UNKNOWN) {
            throw ApiErrors.badRequest("Choose a dry-run strategy before simulating stock impact");
        }
        var stockImpactMode = request != null && request.stockImpactMode() != null
            ? request.stockImpactMode() : defaultStockImpactMode(strategy);
        var negativePolicy = request != null && request.negativeStockPolicy() != null
            ? request.negativeStockPolicy() : DomainEnums.NegativeStockImportPolicy.BLOCK;
        var requestedSnapshotDate = request == null ? null : request.snapshotDate();

        dryRunItems.deleteByTenantIdAndImportSessionId(session.tenantId, session.id);
        dryRuns.deleteByTenantIdAndImportSessionId(session.tenantId, session.id);

        var dryRun = new ImportDryRun();
        dryRun.tenantId = session.tenantId;
        dryRun.importSessionId = session.id;
        dryRun.strategy = strategy;
        dryRun.status = DomainEnums.ImportDryRunStatus.RUNNING;
        dryRun.startedAt = Instant.now();
        dryRun.createdBy = TenantContext.userId();
        dryRun.updatedBy = TenantContext.userId();
        dryRuns.save(dryRun);

        var counters = new Counters();
        var generated = new ArrayList<ImportDryRunItem>();
        try {
            var context = new DryRunContext(session, dryRun, strategy, stockImpactMode, negativePolicy,
                requestedSnapshotDate, counters, generated);
            previewFiles(context);
            previewMasterData(context);
            previewSnapshots(context);
            previewVouchers(context);
            previewCashbookAndAgeing(context);
            previewUnresolvedPlanIssues(context);

            dryRunItems.saveAll(generated);
            counters.set("warnings", generated.stream().filter(item -> item.warningCode != null).count());
            counters.set("blockingErrors", generated.stream().filter(item -> item.errorCode != null
                || item.action == DomainEnums.ImportDryRunAction.BLOCKED).count());
            counters.set("reviewRequiredCount", generated.stream()
                .filter(item -> item.action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED).count());
            dryRun.summaryJson = counters.response(strategy, stockImpactMode, negativePolicy, requestedSnapshotDate);
            dryRun.inputFingerprint = fingerprints.capture(session.tenantId, session.id);
            dryRun.status = DomainEnums.ImportDryRunStatus.COMPLETED;
            dryRun.finishedAt = Instant.now();
            dryRuns.save(dryRun);
            plan.planJson.put("phase", "TRANSACTION_SAFE_DRY_RUN");
            plan.planJson.put("dryRunAvailable", true);
            plan.planJson.put("commitEnabled", false);
            plans.save(plan);
            audit.logCurrent("SMART_IMPORT_DRY_RUN_COMPLETED", "ImportDryRun", dryRun.id, Map.of(
                "sessionId", session.id,
                "strategy", strategy.name(),
                "blockingErrors", counters.get("blockingErrors"),
                "reviewRequired", counters.get("reviewRequiredCount")
            ));
        } catch (RuntimeException ex) {
            dryRun.status = DomainEnums.ImportDryRunStatus.FAILED;
            dryRun.finishedAt = Instant.now();
            dryRun.summaryJson = new LinkedHashMap<>(Map.of(
                "dryRunOnly", true,
                "error", "Dry run could not be completed safely",
                "strategy", strategy.name()
            ));
            dryRuns.save(dryRun);
            audit.logCurrent("SMART_IMPORT_DRY_RUN_FAILED", "ImportDryRun", dryRun.id, Map.of("sessionId", session.id));
        }
        return response(dryRun);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> latest(UUID sessionId) {
        var session = session(sessionId);
        return dryRuns.findFirstByTenantIdAndImportSessionIdOrderByStartedAtDesc(session.tenantId, session.id)
            .map(this::response)
            .orElseGet(() -> new LinkedHashMap<>(Map.of("available", false, "status", "NOT_RUN")));
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> items(
        UUID sessionId,
        DomainEnums.ImportDryRunItemType itemType,
        DomainEnums.ImportDryRunAction action,
        String issueKind,
        UUID sourceFileId,
        Pageable pageable
    ) {
        var session = session(sessionId);
        var dryRun = dryRuns.findFirstByTenantIdAndImportSessionIdOrderByStartedAtDesc(session.tenantId, session.id)
            .orElseThrow(() -> ApiErrors.notFound("Run a dry run before viewing dry-run items"));
        var normalizedIssueKind = normalizeIssueKind(issueKind);
        return dryRunItems.search(session.tenantId, dryRun.id, itemType, action, normalizedIssueKind, sourceFileId, pageable)
            .map(this::itemResponse);
    }

    private void previewFiles(DryRunContext context) {
        for (var file : files.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(context.session.tenantId, context.session.id)) {
            if (file.status == DomainEnums.ImportSessionFileStatus.DUPLICATE) {
                context.counters.inc("duplicateFilesSkipped");
                context.items.add(item(context, DomainEnums.ImportDryRunItemType.FILE, DomainEnums.ImportDryRunAction.SKIP,
                    file.id, null, null, linked("fileName", file.originalFileName, "fileHash", file.fileHash,
                        "reason", "Exact duplicate file hash; only the first copy is simulated."), "DUPLICATE_FILE", null));
            } else if (effectiveType(file) == DomainEnums.DetectedFileType.UNKNOWN || file.status == DomainEnums.ImportSessionFileStatus.FAILED) {
                context.counters.inc("unknownFiles");
                context.items.add(item(context, DomainEnums.ImportDryRunItemType.FILE, DomainEnums.ImportDryRunAction.REVIEW_REQUIRED,
                    file.id, null, null, linked("fileName", file.originalFileName, "reason", file.detectionReason),
                    "UNKNOWN_FILE_TYPE", null));
            } else {
                context.counters.inc("filesProcessed");
            }
        }
    }

    private void previewMasterData(DryRunContext context) {
        for (var row : stagedProducts.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(context.session.tenantId, context.session.id)) {
            var action = action(row.matchStatus);
            if (action == DomainEnums.ImportDryRunAction.CREATE) context.counters.inc("productsToCreate");
            if (action == DomainEnums.ImportDryRunAction.MATCH_EXISTING) context.counters.inc("productsMatchedExisting");
            if (action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED) {
                context.counters.inc("productsReviewRequired");
                if (row.matchStatus == DomainEnums.SmartMatchStatus.POSSIBLE_DUPLICATE_REVIEW
                    || row.matchStatus == DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW) {
                    context.counters.inc("possibleDuplicateProducts");
                }
            }
            context.items.add(item(context, DomainEnums.ImportDryRunItemType.PRODUCT, action, row.sessionFileId,
                row.sourceRowNumber, row.matchedProductId, linked("name", row.rawName, "normalizedName", row.normalizedName,
                    "unitCode", row.unitCode, "sku", row.sku, "matchStatus", row.matchStatus),
                action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED ? "PRODUCT_MATCH_REVIEW_REQUIRED" : null, null));
        }

        for (var row : stagedParties.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(context.session.tenantId, context.session.id)) {
            var action = action(row.matchStatus);
            var itemType = row.partyType == DomainEnums.SmartPartyType.SUPPLIER
                ? DomainEnums.ImportDryRunItemType.SUPPLIER : DomainEnums.ImportDryRunItemType.CUSTOMER;
            if (action == DomainEnums.ImportDryRunAction.CREATE && itemType == DomainEnums.ImportDryRunItemType.CUSTOMER) context.counters.inc("customersToCreate");
            if (action == DomainEnums.ImportDryRunAction.CREATE && itemType == DomainEnums.ImportDryRunItemType.SUPPLIER) context.counters.inc("suppliersToCreate");
            if (action == DomainEnums.ImportDryRunAction.MATCH_EXISTING) context.counters.inc("partiesMatchedExisting");
            if (action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED) context.counters.inc("partiesReviewRequired");
            var targetId = row.matchedCustomerId == null ? row.matchedSupplierId : row.matchedCustomerId;
            context.items.add(item(context, itemType, action, row.sessionFileId, row.sourceRowNumber, targetId,
                linked("name", row.rawName, "partyType", row.partyType, "gstin", row.gstin, "matchStatus", row.matchStatus),
                action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED ? "PARTY_MATCH_REVIEW_REQUIRED" : null, null));
            if (row.sourceType == DomainEnums.DetectedFileType.DEBTOR_CREDITOR_ANALYSIS) {
                if (row.partyType == DomainEnums.SmartPartyType.CUSTOMER) context.counters.inc("debtorRowsProcessed");
                if (row.partyType == DomainEnums.SmartPartyType.SUPPLIER) context.counters.inc("creditorRowsProcessed");
                var snapshotAction = action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED
                    ? DomainEnums.ImportDryRunAction.REVIEW_REQUIRED : DomainEnums.ImportDryRunAction.CREATE;
                if (snapshotAction == DomainEnums.ImportDryRunAction.CREATE) context.counters.inc("outstandingSnapshotsPreviewed");
                context.items.add(item(context, DomainEnums.ImportDryRunItemType.OUTSTANDING_SNAPSHOT,
                    snapshotAction, row.sessionFileId, row.sourceRowNumber, targetId,
                    linked("partyName", row.rawName, "partyType", row.partyType,
                        "snapshotDate", row.snapshotDate, "outstandingAmount", row.openingBalance),
                    snapshotAction == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED ? "OUTSTANDING_PARTY_REVIEW_REQUIRED" : null, null));
            }
        }

        for (var row : stagedWarehouses.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(context.session.tenantId, context.session.id)) {
            var action = action(row.matchStatus);
            if (action == DomainEnums.ImportDryRunAction.CREATE) context.counters.inc("warehousesToCreate");
            if (action == DomainEnums.ImportDryRunAction.MATCH_EXISTING) context.counters.inc("warehousesMatchedExisting");
            if (action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED || row.reviewStatus == DomainEnums.SmartReviewStatus.PENDING) {
                action = DomainEnums.ImportDryRunAction.REVIEW_REQUIRED;
                context.counters.inc("warehousesReviewRequired");
            }
            context.items.add(item(context, DomainEnums.ImportDryRunItemType.WAREHOUSE, action, row.sessionFileId,
                row.sourceRowNumber, row.matchedWarehouseId, linked("name", row.sourceWarehouseName,
                    "normalizedName", row.normalizedName, "matchStatus", row.matchStatus),
                action == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED ? "WAREHOUSE_REVIEW_REQUIRED" : null, null));
        }

        for (var row : stagedUnits.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(context.session.tenantId, context.session.id)) {
            context.items.add(item(context, DomainEnums.ImportDryRunItemType.UNIT, action(row.matchStatus), row.sessionFileId,
                row.sourceRowNumber, row.matchedUnitId, linked("code", row.sourceUnitCode,
                    "normalizedCode", row.normalizedCode, "matchStatus", row.matchStatus), null, null));
        }
    }

    private void previewSnapshots(DryRunContext context) {
        var sessionFiles = files.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(context.session.tenantId, context.session.id).stream()
            .collect(Collectors.toMap(file -> file.id, Function.identity()));
        var tenantWarehouses = warehouses.findByTenantIdAndActiveTrueOrderByNameAsc(context.session.tenantId);
        for (var row : stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(context.session.tenantId, context.session.id)) {
            context.counters.inc("stockSnapshotsProcessed");
            var sourceFile = sessionFiles.get(row.sessionFileId);
            var snapshotDate = firstNonNull(row.snapshotDate,
                sourceFile == null ? null : sourceFile.dateRangeEnd, context.requestedSnapshotDate, LocalDate.now());
            var usedFallbackDate = row.snapshotDate == null && (sourceFile == null || sourceFile.dateRangeEnd == null)
                && context.requestedSnapshotDate == null;
            var warehouseId = row.matchedWarehouseId;
            if (warehouseId == null && (row.warehouseName == null || row.warehouseName.isBlank()) && tenantWarehouses.size() == 1) {
                warehouseId = tenantWarehouses.getFirst().id;
            }
            var unresolvedIdentity = row.matchStatus == DomainEnums.SmartMatchStatus.UNKNOWN
                || row.matchStatus == DomainEnums.SmartMatchStatus.POSSIBLE_DUPLICATE_REVIEW
                || row.matchStatus == DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW;
            if (warehouseId == null && tenantWarehouses.size() > 1) {
                unresolvedIdentity = true;
            }
            var currentStock = row.matchedProductId == null || warehouseId == null ? ZERO
                : stockLedger.currentStock(context.session.tenantId, row.matchedProductId, warehouseId);
            var stockAtDate = row.matchedProductId == null || warehouseId == null ? ZERO
                : stockLedger.stockAt(context.session.tenantId, row.matchedProductId, warehouseId, snapshotDate);
            var imported = nvl(row.importedStock);
            var delta = imported.subtract(stockAtDate);
            var projectedCurrent = currentStock.add(delta);
            var action = delta.signum() == 0 ? DomainEnums.ImportDryRunAction.NO_CHANGE : DomainEnums.ImportDryRunAction.CREATE;
            String warningCode = usedFallbackDate ? "SNAPSHOT_DATE_FALLBACK" : null;
            String errorCode = null;
            if (unresolvedIdentity) {
                action = DomainEnums.ImportDryRunAction.REVIEW_REQUIRED;
                warningCode = "SNAPSHOT_IDENTITY_REVIEW_REQUIRED";
            } else if (imported.signum() < 0) {
                context.counters.inc("negativeStockRowsFound");
                if (context.negativePolicy == DomainEnums.NegativeStockImportPolicy.SKIP_STOCK_MOVEMENT) {
                    action = DomainEnums.ImportDryRunAction.SKIP;
                    warningCode = "NEGATIVE_STOCK_SKIPPED";
                    context.counters.inc("negativeStockRowsSkipped");
                } else if (context.negativePolicy == DomainEnums.NegativeStockImportPolicy.BLOCK
                    || !stockLedger.negativeStockAllowed(context.session.tenantId)) {
                    action = DomainEnums.ImportDryRunAction.BLOCKED;
                    errorCode = "NEGATIVE_STOCK_BLOCKED";
                    context.counters.inc("negativeStockRowsBlocked");
                } else {
                    warningCode = "NEGATIVE_STOCK_IMPORT_AS_IS";
                }
            }
            if (action == DomainEnums.ImportDryRunAction.NO_CHANGE) context.counters.inc("snapshotNoChangeRows");
            if (action == DomainEnums.ImportDryRunAction.CREATE) context.counters.inc("snapshotAdjustmentsPreviewed");
            context.items.add(item(context, DomainEnums.ImportDryRunItemType.STOCK_SNAPSHOT, action, row.sessionFileId,
                row.sourceRowNumber, row.matchedProductId, linked(
                    "productName", row.productName, "unitCode", row.unitCode, "warehouseName", row.warehouseName,
                    "warehouseId", warehouseId, "snapshotDate", snapshotDate.toString(), "currentStock", currentStock,
                    "stockAtSnapshotDate", stockAtDate, "importedSnapshotQuantity", imported,
                    "deltaAtSnapshotDate", delta, "projectedCurrentStockAfterApplyingLaterTransactions", projectedCurrent,
                    "matchStatus", row.matchStatus, "negativeStockPolicy", context.negativePolicy,
                    "sourceFileHash", sourceFile == null ? null : sourceFile.fileHash), warningCode, errorCode));
        }
    }

    private void previewVouchers(DryRunContext context) {
        var vouchers = stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(
            context.session.tenantId, context.session.id);
        var voucherItems = stagedVoucherItems.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(
            context.session.tenantId, context.session.id);
        var itemsByVoucher = voucherItems.stream().collect(Collectors.groupingBy(item -> item.stagedVoucherId, LinkedHashMap::new, Collectors.toList()));
        var snapshots = stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(context.session.tenantId, context.session.id);
        var latestSnapshotDate = snapshots.stream().map(row -> firstNonNull(row.snapshotDate, context.requestedSnapshotDate))
            .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(context.requestedSnapshotDate);
        var projectedStock = new HashMap<String, BigDecimal>();
        var ledgerLinesByVoucher = new HashMap<UUID, Integer>();

        for (var voucher : vouchers) {
            context.counters.add("taxLinesCaptured", voucher.taxLineCount);
            context.counters.add("discountLinesCaptured", voucher.discountLineCount);
            context.counters.add("freightLinesCaptured", voucher.freightLineCount);
            context.counters.add("roundOffLinesCaptured", voucher.roundOffLineCount);
            context.counters.add("otherChargeLinesCaptured", voucher.otherChargeLineCount);
            var invoiceType = invoiceType(voucher.voucherType);
            var sourceItems = itemsByVoucher.getOrDefault(voucher.id, List.of());
            var duplicate = voucher.matchStatus == DomainEnums.SmartMatchStatus.SKIP_DUPLICATE;
            var invoiceAction = voucher.reviewStatus == DomainEnums.SmartReviewStatus.PENDING
                ? DomainEnums.ImportDryRunAction.REVIEW_REQUIRED
                : duplicate || existingInvoice(context.session.tenantId, invoiceType, voucher.voucherNumber) ? DomainEnums.ImportDryRunAction.SKIP
                : DomainEnums.ImportDryRunAction.CREATE;
            if (duplicate) context.counters.inc("duplicateVouchersSkipped");
            if (invoiceAction == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED) context.counters.inc("vouchersBlocked");
            if (invoiceAction == DomainEnums.ImportDryRunAction.CREATE) incrementInvoiceCounter(context.counters, invoiceType);
            context.items.add(item(context, invoiceType, invoiceAction, voucher.sessionFileId, voucher.sourceRowNumber,
                null, linked("voucherType", voucher.voucherType, "voucherNumber", voucher.voucherNumber,
                    "voucherDate", voucher.voucherDate, "partyName", voucher.partyName, "totalAmount", voucher.totalAmount,
                    "invoiceItemCount", sourceItems.size(), "fingerprint", voucher.fingerprint, "externalId", voucher.externalId,
                    "stockImpactMode", context.stockImpactMode, "taxAmount", voucher.taxAmount,
                    "discountAmount", voucher.discountAmount, "freightAmount", voucher.freightAmount,
                    "roundOffAmount", voucher.roundOffAmount, "otherChargesAmount", voucher.otherChargesAmount),
                invoiceAction == DomainEnums.ImportDryRunAction.REVIEW_REQUIRED ? "VOUCHER_REVIEW_REQUIRED"
                    : duplicate ? "DUPLICATE_VOUCHER" : null, null));
            if (invoiceAction != DomainEnums.ImportDryRunAction.CREATE) continue;

            for (var sourceItem : sourceItems) {
                context.counters.inc("voucherItemsReady");
                var rateSource = normalizeRateSource(sourceItem);
                rateCounters(context.counters, rateSource, invoiceType);
                ledgerLinesByVoucher.merge(voucher.id, integer(sourceItem.rawMetadataJson.get("Ledger Lines Skipped")), Math::max);
                var decision = stockDecision(context, voucher, latestSnapshotDate);
                var movementAction = decision.applyMovement ? DomainEnums.ImportDryRunAction.CREATE : DomainEnums.ImportDryRunAction.SKIP;
                var warningCode = decision.warningCode;
                if (warningCode == null) {
                    warningCode = switch (rateSource) {
                        case "DERIVED_FROM_AMOUNT" -> "RATE_DERIVED_FROM_AMOUNT";
                        case "SIGN_NORMALIZED" -> "RATE_SIGN_NORMALIZED";
                        case "ZERO_COST_ITEM" -> "ZERO_COST_ITEM";
                        case "ZERO_RATE_ITEM" -> "ZERO_RATE_ITEM";
                        default -> null;
                    };
                }
                String errorCode = null;
                if ("INVALID".equals(rateSource)) {
                    movementAction = DomainEnums.ImportDryRunAction.BLOCKED;
                    errorCode = "INVALID_RATE";
                    context.counters.inc("invalidRateRows");
                } else if (sourceItem.reviewStatus == DomainEnums.SmartReviewStatus.PENDING
                    && sourceItem.matchStatus != DomainEnums.SmartMatchStatus.CREATE_NEW) {
                    movementAction = DomainEnums.ImportDryRunAction.REVIEW_REQUIRED;
                    warningCode = "VOUCHER_ITEM_REVIEW_REQUIRED";
                }

                var signedQuantity = signedQuantity(invoiceType, nvl(sourceItem.quantity));
                BigDecimal current = ZERO;
                BigDecimal projected = ZERO;
                if (movementAction == DomainEnums.ImportDryRunAction.CREATE && signedQuantity.signum() != 0) {
                    var projectionKey = sourceItem.matchedProductId + "|" + sourceItem.matchedWarehouseId;
                    current = projectedStock.computeIfAbsent(projectionKey, ignored -> sourceItem.matchedProductId == null
                        || sourceItem.matchedWarehouseId == null ? ZERO
                        : stockLedger.currentStock(context.session.tenantId, sourceItem.matchedProductId, sourceItem.matchedWarehouseId));
                    projected = current.add(signedQuantity);
                    if (projected.signum() < 0) {
                        context.counters.inc("negativeStockRowsFound");
                        if (context.negativePolicy == DomainEnums.NegativeStockImportPolicy.SKIP_STOCK_MOVEMENT) {
                            movementAction = DomainEnums.ImportDryRunAction.SKIP;
                            warningCode = "NEGATIVE_STOCK_SKIPPED";
                            context.counters.inc("negativeStockRowsSkipped");
                        } else if (context.negativePolicy == DomainEnums.NegativeStockImportPolicy.BLOCK
                            || !stockLedger.negativeStockAllowed(context.session.tenantId)) {
                            movementAction = DomainEnums.ImportDryRunAction.BLOCKED;
                            errorCode = "NEGATIVE_STOCK_BLOCKED";
                            context.counters.inc("negativeStockRowsBlocked");
                        } else {
                            warningCode = "NEGATIVE_STOCK_IMPORT_AS_IS";
                            projectedStock.put(projectionKey, projected);
                        }
                    } else {
                        projectedStock.put(projectionKey, projected);
                    }
                }
                if (movementAction == DomainEnums.ImportDryRunAction.CREATE) context.counters.inc("stockMovementsPreviewed");
                if (!decision.applyMovement && decision.beforeSnapshot) context.counters.inc("stockMovementsSkippedBeforeSnapshotDate");
                if (!decision.applyMovement && !decision.beforeSnapshot) context.counters.inc("stockMovementsSkippedDueToInvoiceOnly");
                context.items.add(item(context, DomainEnums.ImportDryRunItemType.STOCK_MOVEMENT, movementAction,
                    sourceItem.sessionFileId, sourceItem.sourceRowNumber, sourceItem.matchedProductId, linked(
                        "voucherNumber", voucher.voucherNumber, "voucherDate", voucher.voucherDate,
                        "productName", sourceItem.productName, "warehouseName", sourceItem.warehouseName,
                        "quantity", sourceItem.quantity, "signedQuantity", signedQuantity, "rate", sourceItem.rate,
                        "amount", sourceItem.amount, "rateSource", rateSource, "currentStock", current,
                        "projectedStock", projected, "stockImpactMode", context.stockImpactMode,
                        "reason", decision.reason), warningCode, errorCode));
            }
        }
        context.counters.set("ledgerLinesSkipped", ledgerLinesByVoucher.values().stream().mapToLong(Integer::longValue).sum());
    }

    private void previewCashbookAndAgeing(DryRunContext context) {
        for (var row : stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(context.session.tenantId, context.session.id)) {
            var matched = cashbookReady(row);
            var duplicate = matched && row.fingerprint != null && (row.matchedPartyType == DomainEnums.SmartPartyType.CUSTOMER
                ? customerPayments.findByTenantIdAndSourceFingerprint(context.session.tenantId, row.fingerprint).isPresent()
                : supplierPayments.findByTenantIdAndSourceFingerprint(context.session.tenantId, row.fingerprint).isPresent());
            var action = duplicate ? DomainEnums.ImportDryRunAction.SKIP
                : matched ? DomainEnums.ImportDryRunAction.CREATE : DomainEnums.ImportDryRunAction.SKIP;
            if (matched) {
                context.counters.inc("cashbookRowsReady");
                context.counters.inc("cashbookRowsMatched");
                if (duplicate) context.counters.inc("duplicatePaymentsSkipped");
                else if (row.matchedPartyType == DomainEnums.SmartPartyType.CUSTOMER) context.counters.inc("customerPaymentsPreviewed");
                else context.counters.inc("supplierPaymentsPreviewed");
                if (!duplicate && row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_SALES_INVOICE) {
                    context.counters.inc("paymentsLinkedToSalesInvoices");
                }
                if (!duplicate && row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_PURCHASE_INVOICE) {
                    context.counters.inc("paymentsLinkedToPurchaseInvoices");
                }
            } else if (row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW) {
                context.counters.inc("cashbookRowsReviewRequired");
            } else {
                context.counters.inc("cashbookRowsUnmatched");
            }
            context.items.add(item(context, DomainEnums.ImportDryRunItemType.CASHBOOK_ENTRY,
                action,
                row.sessionFileId, row.sourceRowNumber, row.matchedInvoiceId, linked("date", row.entryDate,
                    "partyName", row.partyName, "amount", row.amount, "direction", row.direction,
                    "paymentMode", row.paymentMode, "referenceNumber", row.referenceNumber,
                    "matchedPartyId", row.matchedPartyId, "matchedPartyType", row.matchedPartyType,
                    "matchedInvoiceId", row.matchedInvoiceId, "cashbookMatchStatus", row.cashbookMatchStatus,
                    "matchConfidence", row.matchConfidence, "matchReason", row.matchReason),
                duplicate ? "DUPLICATE_PAYMENT_SKIPPED" : matched ? null
                    : row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW
                    ? "CASHBOOK_LOW_CONFIDENCE" : "CASHBOOK_UNMATCHED", null));
        }
        for (var row : stagedAgeing.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(context.session.tenantId, context.session.id)) {
            var matched = row.matchedProductId != null;
            context.counters.inc(matched ? "ageingRowsMatched" : "ageingRowsUnmatched");
            context.items.add(item(context, DomainEnums.ImportDryRunItemType.STOCK_AGEING,
                matched ? DomainEnums.ImportDryRunAction.MATCH_EXISTING : DomainEnums.ImportDryRunAction.REVIEW_REQUIRED,
                row.sessionFileId, row.sourceRowNumber, row.matchedProductId, linked("productName", row.productName,
                    "unitCode", row.unitCode, "quantity", row.quantity, "ageingBucket", row.ageingBucket,
                    "daysOld", row.daysOld, "stockValue", row.stockValue), matched ? null : "STOCK_AGEING_UNMATCHED", null));
        }
    }

    private boolean cashbookReady(SmartStagedCashbookEntry row) {
        return row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_CUSTOMER_PAYMENT
            || row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_SUPPLIER_PAYMENT
            || row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_SALES_INVOICE
            || row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_PURCHASE_INVOICE;
    }

    private void previewUnresolvedPlanIssues(DryRunContext context) {
        var representedCodes = context.items.stream()
            .flatMap(item -> java.util.stream.Stream.of(item.warningCode, item.errorCode))
            .filter(Objects::nonNull).collect(Collectors.toSet());
        for (var issue : planIssues.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(context.session.tenantId, context.session.id)) {
            if (issue.resolved || issue.severity == DomainEnums.ImportPlanIssueSeverity.INFO || representedCodes.contains(issue.code)) continue;
            if ("NEGATIVE_STOCK".equals(issue.code)
                && context.negativePolicy == DomainEnums.NegativeStockImportPolicy.SKIP_STOCK_MOVEMENT) continue;
            if ("NEGATIVE_STOCK".equals(issue.code)
                && context.negativePolicy == DomainEnums.NegativeStockImportPolicy.IMPORT_AS_IS
                && stockLedger.negativeStockAllowed(context.session.tenantId)) continue;
            var action = issue.severity == DomainEnums.ImportPlanIssueSeverity.ERROR
                ? DomainEnums.ImportDryRunAction.BLOCKED : issue.severity == DomainEnums.ImportPlanIssueSeverity.REVIEW_REQUIRED
                ? DomainEnums.ImportDryRunAction.REVIEW_REQUIRED : DomainEnums.ImportDryRunAction.SKIP;
            context.items.add(item(context, DomainEnums.ImportDryRunItemType.FILE, action, issue.affectedFileId,
                issue.affectedRows.isEmpty() ? null : issue.affectedRows.getFirst(), null,
                linked("code", issue.code, "message", issue.message, "suggestedAction", issue.suggestedAction),
                issue.severity == DomainEnums.ImportPlanIssueSeverity.ERROR ? null : issue.code,
                issue.severity == DomainEnums.ImportPlanIssueSeverity.ERROR ? issue.code : null));
        }
    }

    private StockDecision stockDecision(DryRunContext context, SmartStagedVoucher voucher, LocalDate snapshotDate) {
        if (context.stockImpactMode == DomainEnums.VoucherStockImpactMode.CREATE_INVOICES_ONLY) {
            var beforeSnapshot = snapshotDate != null && voucher.voucherDate != null && !voucher.voucherDate.isAfter(snapshotDate);
            return new StockDecision(false, beforeSnapshot, beforeSnapshot ? "STOCK_MOVEMENT_SKIPPED_BEFORE_SNAPSHOT"
                : "STOCK_MOVEMENT_SKIPPED_INVOICE_ONLY", beforeSnapshot
                ? "Skipped to avoid double-counting stock before the snapshot date."
                : "Invoice-only mode does not create stock movements.");
        }
        if (context.stockImpactMode == DomainEnums.VoucherStockImpactMode.BLOCK_IF_SNAPSHOT_EXISTS && snapshotDate != null) {
            return new StockDecision(false, true, "STOCK_MOVEMENT_BLOCKED_BY_SNAPSHOT",
                "Stock impact is blocked because this session contains a stock snapshot.");
        }
        if (context.stockImpactMode == DomainEnums.VoucherStockImpactMode.APPLY_STOCK_MOVEMENTS_AFTER_SNAPSHOT_DATE
            && snapshotDate != null && (voucher.voucherDate == null || !voucher.voucherDate.isAfter(snapshotDate))) {
            return new StockDecision(false, true, "STOCK_MOVEMENT_SKIPPED_BEFORE_SNAPSHOT",
                "Skipped to avoid double-counting stock before the snapshot date.");
        }
        return new StockDecision(true, false, null, "A stock movement would be created for this voucher item.");
    }

    private boolean existingInvoice(UUID tenantId, DomainEnums.ImportDryRunItemType type, String number) {
        if (number == null || number.isBlank()) return false;
        return switch (type) {
            case SALES_INVOICE -> salesInvoices.existsByTenantIdAndInvoiceNumberIgnoreCase(tenantId, number);
            case PURCHASE_INVOICE -> purchaseInvoices.existsByTenantIdAndInvoiceNumberIgnoreCase(tenantId, number);
            case CREDIT_NOTE -> financialAdjustments.findByTenantIdAndAdjustmentTypeAndVoucherNumberIgnoreCase(
                tenantId, DomainEnums.FinancialAdjustmentType.CREDIT_NOTE, number).isPresent();
            case DEBIT_NOTE -> financialAdjustments.findByTenantIdAndAdjustmentTypeAndVoucherNumberIgnoreCase(
                tenantId, DomainEnums.FinancialAdjustmentType.DEBIT_NOTE, number).isPresent();
            default -> false;
        };
    }

    private DomainEnums.ImportDryRunItemType invoiceType(String voucherType) {
        var normalized = voucherType == null ? "" : voucherType.toUpperCase(Locale.ROOT);
        if (normalized.contains("PURCHASE")) return DomainEnums.ImportDryRunItemType.PURCHASE_INVOICE;
        if (normalized.contains("CREDIT")) return DomainEnums.ImportDryRunItemType.CREDIT_NOTE;
        if (normalized.contains("DEBIT")) return DomainEnums.ImportDryRunItemType.DEBIT_NOTE;
        return DomainEnums.ImportDryRunItemType.SALES_INVOICE;
    }

    private BigDecimal signedQuantity(DomainEnums.ImportDryRunItemType type, BigDecimal quantity) {
        return switch (type) {
            case PURCHASE_INVOICE, CREDIT_NOTE -> quantity.abs();
            case SALES_INVOICE, DEBIT_NOTE -> quantity.abs().negate();
            default -> ZERO;
        };
    }

    private void incrementInvoiceCounter(Counters counters, DomainEnums.ImportDryRunItemType type) {
        switch (type) {
            case PURCHASE_INVOICE -> counters.inc("purchaseInvoicesPreviewed");
            case SALES_INVOICE -> counters.inc("salesInvoicesPreviewed");
            case CREDIT_NOTE -> counters.inc("creditNotesPreviewed");
            case DEBIT_NOTE -> counters.inc("debitNotesPreviewed");
            default -> { }
        }
    }

    private String normalizeRateSource(SmartStagedVoucherItem item) {
        if (item.rateSource != null && !item.rateSource.isBlank()) return item.rateSource.toUpperCase(Locale.ROOT);
        if (item.rate != null && item.rate.signum() > 0) return "RATE_FIELD";
        if (item.rate != null && item.rate.signum() == 0) return "ZERO_RATE_ITEM";
        return "INVALID";
    }

    private void rateCounters(Counters counters, String rateSource, DomainEnums.ImportDryRunItemType invoiceType) {
        switch (rateSource) {
            case "DERIVED_FROM_AMOUNT" -> counters.inc("ratesDerived");
            case "SIGN_NORMALIZED" -> counters.inc("ratesSignNormalized");
            case "ZERO_COST_ITEM" -> counters.inc("zeroCostItems");
            case "ZERO_RATE_ITEM" -> {
                if (invoiceType == DomainEnums.ImportDryRunItemType.PURCHASE_INVOICE) counters.inc("zeroCostItems");
                else counters.inc("zeroRateItems");
            }
            default -> { }
        }
    }

    private DomainEnums.ImportDryRunAction action(DomainEnums.SmartMatchStatus matchStatus) {
        if (matchStatus == null) return DomainEnums.ImportDryRunAction.REVIEW_REQUIRED;
        return switch (matchStatus) {
            case CREATE_NEW -> DomainEnums.ImportDryRunAction.CREATE;
            case MATCH_EXISTING -> DomainEnums.ImportDryRunAction.MATCH_EXISTING;
            case SKIP_DUPLICATE -> DomainEnums.ImportDryRunAction.SKIP;
            case POSSIBLE_DUPLICATE_REVIEW, AMBIGUOUS_REVIEW, UNKNOWN -> DomainEnums.ImportDryRunAction.REVIEW_REQUIRED;
        };
    }

    private ImportDryRunItem item(
        DryRunContext context,
        DomainEnums.ImportDryRunItemType itemType,
        DomainEnums.ImportDryRunAction action,
        UUID sourceFileId,
        Integer sourceRowNumber,
        UUID targetEntityId,
        Map<String, Object> preview,
        String warningCode,
        String errorCode
    ) {
        var item = new ImportDryRunItem();
        item.tenantId = context.session.tenantId;
        item.importSessionId = context.session.id;
        item.dryRunId = context.dryRun.id;
        item.itemType = itemType;
        item.action = action;
        item.sourceFileId = sourceFileId;
        item.sourceRowNumber = sourceRowNumber;
        item.targetEntityId = targetEntityId;
        item.previewJson = preview;
        item.warningCode = warningCode;
        item.errorCode = errorCode;
        item.createdBy = TenantContext.userId();
        item.updatedBy = TenantContext.userId();
        return item;
    }

    private Map<String, Object> response(ImportDryRun dryRun) {
        return linked("available", true, "id", dryRun.id, "importSessionId", dryRun.importSessionId,
            "status", dryRun.status, "strategy", dryRun.strategy, "startedAt", dryRun.startedAt,
            "finishedAt", dryRun.finishedAt, "inputFingerprint", dryRun.inputFingerprint,
            "summary", dryRun.summaryJson);
    }

    private Map<String, Object> itemResponse(ImportDryRunItem item) {
        return linked("id", item.id, "itemType", item.itemType, "action", item.action,
            "sourceFileId", item.sourceFileId, "sourceRowNumber", item.sourceRowNumber,
            "targetEntityId", item.targetEntityId, "preview", item.previewJson,
            "warningCode", item.warningCode, "errorCode", item.errorCode, "createdAt", item.createdAt);
    }

    private ImportSession session(UUID sessionId) {
        return sessions.findByTenantIdAndId(TenantContext.tenantId(), sessionId)
            .orElseThrow(() -> ApiErrors.notFound("Import session not found"));
    }

    private DomainEnums.VoucherStockImpactMode defaultStockImpactMode(DomainEnums.ImportStrategy strategy) {
        return switch (strategy) {
            case SNAPSHOT_FIRST -> DomainEnums.VoucherStockImpactMode.CREATE_INVOICES_ONLY;
            case HYBRID_RECONCILIATION -> DomainEnums.VoucherStockImpactMode.APPLY_STOCK_MOVEMENTS_AFTER_SNAPSHOT_DATE;
            case TRANSACTION_HISTORY -> DomainEnums.VoucherStockImpactMode.CREATE_INVOICES_AND_STOCK_MOVEMENTS;
            case UNKNOWN -> throw ApiErrors.badRequest("Choose a dry-run strategy before simulating stock impact");
        };
    }

    private String normalizeIssueKind(String issueKind) {
        if (issueKind == null || issueKind.isBlank()) return null;
        var normalized = issueKind.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ERROR", "WARNING", "REVIEW_REQUIRED").contains(normalized)) {
            throw ApiErrors.badRequest("Issue filter must be ERROR, WARNING, or REVIEW_REQUIRED");
        }
        return normalized;
    }

    private DomainEnums.DetectedFileType effectiveType(ImportSessionFile file) {
        return file.selectedFileType == null ? file.detectedFileType : file.selectedFileType;
    }

    private Map<String, Object> linked(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1] == null ? "" : values[index + 1]);
        }
        return result;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (var value : values) if (value != null) return value;
        return null;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private int integer(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record StockDecision(boolean applyMovement, boolean beforeSnapshot, String warningCode, String reason) { }

    private record DryRunContext(
        ImportSession session,
        ImportDryRun dryRun,
        DomainEnums.ImportStrategy strategy,
        DomainEnums.VoucherStockImpactMode stockImpactMode,
        DomainEnums.NegativeStockImportPolicy negativePolicy,
        LocalDate requestedSnapshotDate,
        Counters counters,
        List<ImportDryRunItem> items
    ) { }

    private static final class Counters {
        private final LinkedHashMap<String, Long> values = new LinkedHashMap<>();

        private Counters() {
            SUMMARY_KEYS.forEach(key -> values.put(key, 0L));
        }

        private void inc(String key) {
            values.compute(key, (ignored, current) -> current == null ? 1L : current + 1L);
        }

        private void set(String key, long value) {
            values.put(key, value);
        }

        private void add(String key, long value) {
            values.merge(key, value, Long::sum);
        }

        private long get(String key) {
            return values.getOrDefault(key, 0L);
        }

        private Map<String, Object> response(
            DomainEnums.ImportStrategy strategy,
            DomainEnums.VoucherStockImpactMode stockImpactMode,
            DomainEnums.NegativeStockImportPolicy negativePolicy,
            LocalDate requestedSnapshotDate
        ) {
            var response = new LinkedHashMap<String, Object>();
            response.putAll(values);
            response.put("strategy", strategy.name());
            response.put("stockImpactMode", stockImpactMode.name());
            response.put("negativeStockPolicy", negativePolicy.name());
            response.put("requestedSnapshotDate", requestedSnapshotDate == null ? "" : requestedSnapshotDate.toString());
            response.put("dryRunOnly", true);
            response.put("finalBusinessWrites", 0);
            return response;
        }
    }
}
