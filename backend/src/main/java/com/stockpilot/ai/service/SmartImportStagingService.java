package com.stockpilot.ai.service;

import com.stockpilot.ai.config.TenantContext;
import com.stockpilot.ai.domain.*;
import com.stockpilot.ai.exception.ApiErrors;
import com.stockpilot.ai.repo.Repositories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SmartImportStagingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int PREVIEW_LIMIT = 100;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.BASIC_ISO_DATE,
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d-M-uuuu"),
        DateTimeFormatter.ofPattern("d/M/uuuu")
    );

    private final Repositories.ImportSessionRepository sessions;
    private final Repositories.ImportSessionFileRepository files;
    private final Repositories.ImportPlanRepository plans;
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
    private final Repositories.SmartImportResolutionRepository resolutions;
    private final Repositories.ImportDryRunRepository dryRuns;
    private final Repositories.ImportDryRunItemRepository dryRunItems;
    private final Repositories.ProductRepository products;
    private final Repositories.ProductBarcodeRepository barcodes;
    private final Repositories.UnitRepository units;
    private final Repositories.CustomerRepository customers;
    private final Repositories.SupplierRepository suppliers;
    private final Repositories.WarehouseRepository warehouses;
    private final Repositories.SalesInvoiceRepository salesInvoices;
    private final Repositories.PurchaseInvoiceRepository purchaseInvoices;
    private final Repositories.FinancialAdjustmentRepository financialAdjustments;
    private final SmartImportCanonicalParserService parser;
    private final ImportPlanBuilderService planBuilder;
    private final StockLedgerService stockLedger;
    private final ObjectStorageService storage;
    private final AuditService audit;

    public SmartImportStagingService(
        Repositories.ImportSessionRepository sessions,
        Repositories.ImportSessionFileRepository files,
        Repositories.ImportPlanRepository plans,
        Repositories.ImportPlanIssueRepository issues,
        Repositories.SmartStagedProductRepository stagedProducts,
        Repositories.SmartStagedPartyRepository stagedParties,
        Repositories.SmartStagedWarehouseRepository stagedWarehouses,
        Repositories.SmartStagedUnitRepository stagedUnits,
        Repositories.SmartStagedStockSnapshotRepository stagedSnapshots,
        Repositories.SmartStagedVoucherRepository stagedVouchers,
        Repositories.SmartStagedVoucherItemRepository stagedVoucherItems,
        Repositories.SmartStagedCashbookEntryRepository stagedCashbook,
        Repositories.SmartStagedStockAgeingRepository stagedAgeing,
        Repositories.SmartImportResolutionRepository resolutions,
        Repositories.ImportDryRunRepository dryRuns,
        Repositories.ImportDryRunItemRepository dryRunItems,
        Repositories.ProductRepository products,
        Repositories.ProductBarcodeRepository barcodes,
        Repositories.UnitRepository units,
        Repositories.CustomerRepository customers,
        Repositories.SupplierRepository suppliers,
        Repositories.WarehouseRepository warehouses,
        Repositories.SalesInvoiceRepository salesInvoices,
        Repositories.PurchaseInvoiceRepository purchaseInvoices,
        Repositories.FinancialAdjustmentRepository financialAdjustments,
        SmartImportCanonicalParserService parser,
        ImportPlanBuilderService planBuilder,
        StockLedgerService stockLedger,
        ObjectStorageService storage,
        AuditService audit
    ) {
        this.sessions = sessions;
        this.files = files;
        this.plans = plans;
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
        this.resolutions = resolutions;
        this.dryRuns = dryRuns;
        this.dryRunItems = dryRunItems;
        this.products = products;
        this.barcodes = barcodes;
        this.units = units;
        this.customers = customers;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.salesInvoices = salesInvoices;
        this.purchaseInvoices = purchaseInvoices;
        this.financialAdjustments = financialAdjustments;
        this.parser = parser;
        this.planBuilder = planBuilder;
        this.stockLedger = stockLedger;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> stage(UUID sessionId) {
        return rebuild(sessionId);
    }

    @Transactional
    public Map<String, Object> rebuild(UUID sessionId) {
        var session = session(sessionId);
        ensureMutable(session);
        var sessionFiles = files.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(session.tenantId, session.id);
        if (sessionFiles.isEmpty()) {
            throw ApiErrors.badRequest("Upload files before staging the session");
        }
        if (sessionFiles.stream().anyMatch(file -> file.status == DomainEnums.ImportSessionFileStatus.UPLOADED)) {
            throw ApiErrors.badRequest("Classify uploaded files before staging the session");
        }

        clearStaging(session.tenantId, session.id);
        var parseFailures = new ArrayList<ParseFailure>();
        var productIndex = new LinkedHashMap<String, SmartStagedProduct>();
        var partyIndex = new LinkedHashMap<String, SmartStagedParty>();
        var warehouseIndex = new LinkedHashMap<String, SmartStagedWarehouse>();
        var unitIndex = new LinkedHashMap<String, SmartStagedUnit>();
        var voucherIndex = new LinkedHashMap<String, SmartStagedVoucher>();

        for (var file : sessionFiles) {
            if (file.status == DomainEnums.ImportSessionFileStatus.DUPLICATE || effectiveType(file) == DomainEnums.DetectedFileType.UNKNOWN) {
                continue;
            }
            try {
                var rows = parser.parse(session.tenantId, session.id, file);
                stageRows(session, file, rows, productIndex, partyIndex, warehouseIndex, unitIndex, voucherIndex);
            } catch (Exception ex) {
                parseFailures.add(new ParseFailure(file, safeMessage(ex)));
            }
        }

        matchCanonicalRecords(session);
        planBuilder.build(session);
        var generated = buildReviewItems(session, parseFailures);
        issues.saveAll(generated);
        applySessionReviewState(session);
        audit.logCurrent("SMART_IMPORT_STAGED", "ImportSession", session.id, summary(session));
        return workspace(session.id);
    }

    @Transactional
    public Map<String, Object> overrideFileType(UUID sessionId, UUID fileId, DomainEnums.DetectedFileType selectedType) {
        var session = session(sessionId);
        ensureMutable(session);
        var file = files.findByTenantIdAndImportSessionIdAndId(session.tenantId, session.id, fileId)
            .orElseThrow(() -> ApiErrors.notFound("Import session file not found"));
        file.selectedFileType = Objects.requireNonNull(selectedType, "selectedFileType");
        file.status = DomainEnums.ImportSessionFileStatus.CLASSIFIED;
        file.confidence = BigDecimal.ONE;
        file.detectionReason = "File type manually selected as " + selectedType.name() + ".";
        file.updatedBy = TenantContext.userId();
        files.save(file);
        audit.logCurrent("SMART_IMPORT_FILE_TYPE_OVERRIDDEN", "ImportSessionFile", file.id, Map.of("selectedFileType", selectedType.name()));
        return rebuild(session.id);
    }

    @Transactional
    public Map<String, Object> removeFile(UUID sessionId, UUID fileId) {
        var session = session(sessionId);
        ensureMutable(session);
        var file = files.findByTenantIdAndImportSessionIdAndId(session.tenantId, session.id, fileId)
            .orElseThrow(() -> ApiErrors.notFound("Import session file not found"));
        var removedFileName = file.originalFileName;
        var removedStorageKey = file.storageKey;
        clearStaging(session.tenantId, session.id);
        issues.deleteByTenantIdAndImportSessionId(session.tenantId, session.id);
        plans.findByTenantIdAndImportSessionId(session.tenantId, session.id).ifPresent(plans::delete);
        files.delete(file);
        files.flush();
        if (removedStorageKey != null && !removedStorageKey.isBlank()) {
            try {
                storage.delete(removedStorageKey);
            } catch (IOException ex) {
                audit.logCurrent("SMART_IMPORT_FILE_STORAGE_DELETE_FAILED", "ImportSessionFile", fileId, Map.of(
                    "fileName", removedFileName,
                    "storageKey", removedStorageKey,
                    "error", safeMessage(ex)
                ));
            }
        }
        var remaining = files.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(session.tenantId, session.id);
        if (remaining.isEmpty()) {
            session.status = DomainEnums.ImportSessionStatus.CREATED;
            session.recommendedStrategy = DomainEnums.ImportStrategy.UNKNOWN;
            sessions.save(session);
        } else if (remaining.stream().anyMatch(row -> row.status == DomainEnums.ImportSessionFileStatus.UPLOADED)) {
            session.status = DomainEnums.ImportSessionStatus.FILES_UPLOADED;
            session.recommendedStrategy = DomainEnums.ImportStrategy.UNKNOWN;
            sessions.save(session);
        } else {
            session.recommendedStrategy = DomainEnums.ImportStrategy.UNKNOWN;
            planBuilder.rebuildClassificationIssues(session, remaining);
        }
        audit.logCurrent("SMART_IMPORT_FILE_REMOVED", "ImportSessionFile", fileId, Map.of(
            "fileName", removedFileName,
            "remainingFileCount", remaining.size()
        ));
        return workspace(session.id);
    }

    @Transactional
    public Map<String, Object> resolve(UUID sessionId, UUID itemId, String action, UUID targetId, DomainEnums.DetectedFileType selectedFileType) {
        var session = session(sessionId);
        ensureMutable(session);
        var item = issues.findByTenantIdAndImportSessionIdAndId(session.tenantId, session.id, itemId)
            .orElseThrow(() -> ApiErrors.notFound("Review item not found"));
        var normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        var allowed = item.availableChoices.stream().map(choice -> String.valueOf(choice.get("action"))).toList();
        if (!allowed.contains(normalizedAction)) {
            throw ApiErrors.badRequest("This review item does not support the selected resolution");
        }
        if ("OVERRIDE_FILE_TYPE".equals(normalizedAction)) {
            if (selectedFileType == null || item.affectedFileId == null) {
                throw ApiErrors.badRequest("Select a file type for this resolution");
            }
            return overrideFileType(session.id, item.affectedFileId, selectedFileType);
        }

        var resolutionKey = String.valueOf(item.contextJson.getOrDefault("resolutionKey", "ISSUE:" + item.id));
        validateResolutionTarget(session.tenantId, normalizedAction, targetId);
        var resolution = resolutions.findByTenantIdAndImportSessionIdAndResolutionKey(session.tenantId, session.id, resolutionKey)
            .orElseGet(SmartImportResolution::new);
        resolution.tenantId = session.tenantId;
        resolution.importSessionId = session.id;
        resolution.resolutionKey = resolutionKey;
        resolution.action = normalizedAction;
        resolution.targetId = targetId;
        resolution.detailsJson = new LinkedHashMap<>(Map.of("sourceIssueCode", item.code));
        resolution.updatedBy = TenantContext.userId();
        if (resolution.createdBy == null) {
            resolution.createdBy = TenantContext.userId();
        }
        resolutions.save(resolution);
        item.resolved = true;
        item.resolutionJson = new LinkedHashMap<>();
        item.resolutionJson.put("action", normalizedAction);
        if (targetId != null) {
            item.resolutionJson.put("targetId", targetId.toString());
        }
        issues.save(item);
        audit.logCurrent("SMART_IMPORT_REVIEW_RESOLVED", "ImportPlanIssue", item.id, item.resolutionJson);
        return rebuild(session.id);
    }

    public List<Map<String, Object>> reviewItems(UUID sessionId) {
        var session = session(sessionId);
        var names = files.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(session.tenantId, session.id).stream()
            .collect(java.util.stream.Collectors.toMap(file -> file.id, file -> file.originalFileName));
        return issues.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(session.tenantId, session.id).stream()
            .filter(issue -> issue.severity != DomainEnums.ImportPlanIssueSeverity.INFO)
            .map(issue -> reviewItem(issue, names.get(issue.affectedFileId)))
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> workspace(UUID sessionId) {
        var session = session(sessionId);
        var response = new LinkedHashMap<String, Object>();
        response.put("summary", summary(session));
        response.put("matching", matchingSummary(session));
        response.put("products", stagedProducts.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::productRow).toList());
        response.put("parties", stagedParties.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::partyRow).toList());
        response.put("warehouses", stagedWarehouses.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::warehouseRow).toList());
        response.put("units", stagedUnits.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::unitRow).toList());
        response.put("snapshots", stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::snapshotRow).toList());
        response.put("vouchers", stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::voucherRow).toList());
        response.put("voucherItems", stagedVoucherItems.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::voucherItemRow).toList());
        response.put("cashbookEntries", stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::cashbookRow).toList());
        response.put("stockAgeing", stagedAgeing.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id).stream().limit(PREVIEW_LIMIT).map(this::ageingRow).toList());
        response.put("reviewItems", reviewItems(session.id));
        response.put("previewLimit", PREVIEW_LIMIT);
        return response;
    }

    private void stageRows(
        ImportSession session,
        ImportSessionFile file,
        List<Map<String, String>> rows,
        Map<String, SmartStagedProduct> productIndex,
        Map<String, SmartStagedParty> partyIndex,
        Map<String, SmartStagedWarehouse> warehouseIndex,
        Map<String, SmartStagedUnit> unitIndex,
        Map<String, SmartStagedVoucher> voucherIndex
    ) {
        var type = effectiveType(file);
        for (int index = 0; index < rows.size(); index++) {
            var rowNumber = index + 1;
            var row = rows.get(index);
            switch (type) {
                case INVENTORY_MASTER -> stageInventoryRow(session, file, type, rowNumber, row, productIndex, warehouseIndex, unitIndex);
                case ACCOUNTING_MASTER -> stagePartyCandidate(session, file, type, rowNumber, row, partyType(type, row), partyIndex);
                case STOCK_SNAPSHOT -> stageSnapshotRow(session, file, type, rowNumber, row, productIndex, warehouseIndex, unitIndex);
                case SALES_VOUCHERS, PURCHASE_VOUCHERS, CREDIT_NOTES, DEBIT_NOTES, STOCK_JOURNAL ->
                    stageVoucherRow(session, file, type, rowNumber, row, productIndex, partyIndex, warehouseIndex, unitIndex, voucherIndex);
                case CASH_BOOK -> stageCashbookRow(session, file, rowNumber, row);
                case DEBTOR_CREDITOR_ANALYSIS -> stagePartyCandidate(session, file, type, rowNumber, row, partyType(type, row), partyIndex);
                case STOCK_AGEING -> stageAgeingRow(session, file, type, rowNumber, row, productIndex, unitIndex);
                case UNKNOWN -> { }
            }
        }
    }

    private void stageInventoryRow(
        ImportSession session, ImportSessionFile file, DomainEnums.DetectedFileType type, int rowNumber, Map<String, String> row,
        Map<String, SmartStagedProduct> productIndex, Map<String, SmartStagedWarehouse> warehouseIndex, Map<String, SmartStagedUnit> unitIndex
    ) {
        stageProductCandidate(session, file, type, rowNumber, row, productIndex);
        stageUnitCandidate(session, file, rowNumber, lookup(row, "Unit", "Unit Code", "Base Unit", "BASEUNITS"), unitIndex);
        stageWarehouseCandidate(session, file, rowNumber, lookup(row, "Warehouse", "Godown", "Godown Name"), warehouseIndex);
    }

    private void stageSnapshotRow(
        ImportSession session, ImportSessionFile file, DomainEnums.DetectedFileType type, int rowNumber, Map<String, String> row,
        Map<String, SmartStagedProduct> productIndex, Map<String, SmartStagedWarehouse> warehouseIndex, Map<String, SmartStagedUnit> unitIndex
    ) {
        var product = stageProductCandidate(session, file, type, rowNumber, row, productIndex);
        var unitCode = firstNonBlank(lookup(row, "Unit", "Unit Code", "Base Unit"), product == null ? null : product.unitCode);
        var warehouseName = lookup(row, "Warehouse", "Godown", "Godown Name");
        stageUnitCandidate(session, file, rowNumber, unitCode, unitIndex);
        stageWarehouseCandidate(session, file, rowNumber, warehouseName, warehouseIndex);
        var snapshot = new SmartStagedStockSnapshot();
        own(snapshot, session, file, rowNumber);
        snapshot.productName = lookup(row, "Item Name", "Product Name", "Name");
        snapshot.normalizedProductName = normalizeName(snapshot.productName);
        snapshot.unitCode = normalizeUnit(unitCode);
        snapshot.warehouseName = blankToNull(warehouseName);
        snapshot.snapshotDate = file.dateRangeEnd;
        snapshot.importedStock = nvl(decimal(lookup(row, "Opening Stock", "Closing Qty", "Closing Quantity", "Quantity", "Qty")));
        snapshot.rate = decimal(lookup(row, "Purchase Price", "Closing Rate", "Rate"));
        snapshot.stockValue = decimal(lookup(row, "Stock Value", "Closing Value", "Value", "Amount"));
        snapshot.rawMetadataJson = metadata(row);
        stagedSnapshots.save(snapshot);
    }

    private void stageVoucherRow(
        ImportSession session, ImportSessionFile file, DomainEnums.DetectedFileType type, int rowNumber, Map<String, String> row,
        Map<String, SmartStagedProduct> productIndex, Map<String, SmartStagedParty> partyIndex,
        Map<String, SmartStagedWarehouse> warehouseIndex, Map<String, SmartStagedUnit> unitIndex,
        Map<String, SmartStagedVoucher> voucherIndex
    ) {
        var voucherType = firstNonBlank(lookup(row, "Voucher Type", "Type"), defaultVoucherType(type));
        var voucherNumber = lookup(row, "Voucher Number", "Voucher No", "Invoice Number", "Invoice No");
        var voucherDate = date(lookup(row, "Voucher Date", "Date", "Invoice Date"));
        var partyName = lookup(row, "Party Name", "Customer", "Supplier", "Ledger Name");
        var voucherKey = normalize(voucherType) + "|" + normalize(voucherNumber) + "|" + voucherDate + "|" + normalizeName(partyName);
        if (voucherNumber == null || voucherNumber.isBlank()) {
            voucherKey += "|ROW:" + rowNumber;
        }
        var voucher = voucherIndex.get(voucherKey);
        if (voucher == null) {
            voucher = new SmartStagedVoucher();
            own(voucher, session, file, rowNumber);
            voucher.voucherType = voucherType;
            voucher.voucherNumber = blankToNull(voucherNumber);
            voucher.voucherDate = voucherDate;
            voucher.partyName = blankToNull(partyName);
            voucher.normalizedPartyName = normalizeName(partyName);
            voucher.totalAmount = ZERO;
            voucher.externalId = blankToNull(lookup(row, "Tally GUID", "GUID", "MASTERID", "Master ID"));
            voucher.rawMetadataJson = metadata(row);
            voucher.fingerprint = fingerprint(session.tenantId, voucher);
            stagedVouchers.save(voucher);
            voucherIndex.put(voucherKey, voucher);
        }
        var lineAmount = decimal(lookup(row, "Amount", "Line Amount", "Total Amount"));
        mergeVoucherFinancials(session.tenantId, voucher, row);
        stagePartyCandidate(session, file, type, rowNumber, row, partyType(type, row), partyIndex);

        var productName = lookup(row, "Item Name", "Product Name", "Stock Item", "STOCKITEMNAME");
        if (productName == null || productName.isBlank()) {
            voucher.fingerprint = fingerprint(session.tenantId, voucher);
            stagedVouchers.save(voucher);
            return;
        }
        var product = stageProductCandidate(session, file, type, rowNumber, row, productIndex);
        var unitCode = firstNonBlank(lookup(row, "Unit", "Unit Code"), product == null ? null : product.unitCode);
        var warehouseName = lookup(row, "Warehouse", "Godown", "Godown Name");
        stageUnitCandidate(session, file, rowNumber, unitCode, unitIndex);
        stageWarehouseCandidate(session, file, rowNumber, warehouseName, warehouseIndex);
        var item = new SmartStagedVoucherItem();
        own(item, session, file, rowNumber);
        item.stagedVoucherId = voucher.id;
        item.productName = productName;
        item.normalizedProductName = normalizeName(productName);
        item.unitCode = normalizeUnit(unitCode);
        item.quantity = abs(decimal(lookup(row, "Qty", "Quantity", "Billed Quantity", "Actual Quantity")));
        item.rate = decimal(lookup(row, "Rate", "Parsed Rate", "Unit Price", "Price"));
        item.amount = lineAmount;
        item.warehouseName = blankToNull(warehouseName);
        item.rateSource = firstNonBlank(lookup(row, "Rate Source"), item.rate == null ? "INVALID" : item.rate.signum() == 0 ? "ZERO_COST_ITEM" : "RATE_FIELD");
        item.rawMetadataJson = metadata(row);
        stagedVoucherItems.save(item);
        var calculatedAmount = lineAmount == null && item.quantity != null && item.rate != null
            ? item.quantity.multiply(item.rate) : lineAmount;
        voucher.totalAmount = nvl(voucher.totalAmount).add(nvl(calculatedAmount).abs());
        voucher.fingerprint = fingerprint(session.tenantId, voucher);
        stagedVouchers.save(voucher);
    }

    private void mergeVoucherFinancials(UUID tenantId, SmartStagedVoucher voucher, Map<String, String> row) {
        var taxLines = Optional.ofNullable(integer(lookup(row, "Tax Line Count"))).orElse(0);
        var discountLines = Optional.ofNullable(integer(lookup(row, "Discount Line Count"))).orElse(0);
        var freightLines = Optional.ofNullable(integer(lookup(row, "Freight Line Count"))).orElse(0);
        var roundOffLines = Optional.ofNullable(integer(lookup(row, "Round Off Line Count"))).orElse(0);
        var otherLines = Optional.ofNullable(integer(lookup(row, "Other Charge Line Count"))).orElse(0);
        if (taxLines + discountLines + freightLines + roundOffLines + otherLines == 0
            || voucher.taxLineCount + voucher.discountLineCount + voucher.freightLineCount
                + voucher.roundOffLineCount + voucher.otherChargeLineCount > 0) return;
        voucher.taxAmount = nvl(decimal(lookup(row, "Tax Amount"))).abs();
        voucher.discountAmount = nvl(decimal(lookup(row, "Discount Amount"))).abs();
        voucher.freightAmount = nvl(decimal(lookup(row, "Freight Amount"))).abs();
        voucher.roundOffAmount = nvl(decimal(lookup(row, "Round Off Amount")));
        voucher.otherChargesAmount = nvl(decimal(lookup(row, "Other Charges Amount"))).abs();
        voucher.taxLineCount = taxLines;
        voucher.discountLineCount = discountLines;
        voucher.freightLineCount = freightLines;
        voucher.roundOffLineCount = roundOffLines;
        voucher.otherChargeLineCount = otherLines;
        voucher.totalAmount = nvl(voucher.totalAmount).add(voucher.taxAmount).subtract(voucher.discountAmount)
            .add(voucher.freightAmount).add(voucher.roundOffAmount).add(voucher.otherChargesAmount);
        voucher.rawMetadataJson.put("ledgerAdjustmentDetails", lookup(row, "Ledger Adjustment Details"));
        voucher.rawMetadataJson.put("financialComponentsCaptured", true);
        voucher.fingerprint = fingerprint(tenantId, voucher);
    }

    private void stageCashbookRow(ImportSession session, ImportSessionFile file, int rowNumber, Map<String, String> row) {
        var entry = new SmartStagedCashbookEntry();
        own(entry, session, file, rowNumber);
        entry.entryDate = date(lookup(row, "Date", "Voucher Date", "Entry Date"));
        entry.partyName = blankToNull(lookup(row, "Party Name", "Ledger Name", "Account"));
        entry.gstin = blankToNull(lookup(row, "GSTIN", "GST No", "GST Number"));
        var signedAmount = decimal(lookup(row, "Amount", "Value"));
        entry.amount = abs(signedAmount);
        var type = normalize(lookup(row, "Voucher Type", "Direction", "Type"));
        entry.direction = type.contains("RECEIPT") ? DomainEnums.SmartCashbookDirection.RECEIPT
            : type.contains("PAYMENT") ? DomainEnums.SmartCashbookDirection.PAYMENT
            : signedAmount != null && signedAmount.signum() < 0 ? DomainEnums.SmartCashbookDirection.PAYMENT
            : DomainEnums.SmartCashbookDirection.UNKNOWN;
        entry.referenceNumber = blankToNull(lookup(row, "Reference Number", "Reference", "Voucher Number", "Bill Ref", "Bill Number"));
        entry.paymentMode = paymentMode(lookup(row, "Payment Mode", "Mode", "Instrument Type", "Transaction Type"), entry.partyName);
        entry.fingerprint = cashbookFingerprint(session.tenantId, entry);
        entry.rawMetadataJson = metadata(row);
        stagedCashbook.save(entry);
    }

    private void stageAgeingRow(
        ImportSession session, ImportSessionFile file, DomainEnums.DetectedFileType type, int rowNumber, Map<String, String> row,
        Map<String, SmartStagedProduct> productIndex, Map<String, SmartStagedUnit> unitIndex
    ) {
        var product = stageProductCandidate(session, file, type, rowNumber, row, productIndex);
        var item = new SmartStagedStockAgeing();
        own(item, session, file, rowNumber);
        item.productName = lookup(row, "Item Name", "Product Name", "Name");
        item.normalizedProductName = normalizeName(item.productName);
        item.unitCode = normalizeUnit(firstNonBlank(lookup(row, "Unit", "Unit Code"), product == null ? null : product.unitCode));
        item.quantity = decimal(lookup(row, "Quantity", "Qty", "Stock"));
        item.ageingBucket = blankToNull(lookup(row, "Ageing Bucket", "Aging Bucket", "Bucket"));
        item.daysOld = integer(lookup(row, "Days", "Days Old", "Age"));
        item.stockValue = decimal(lookup(row, "Value", "Stock Value", "Amount"));
        item.rawMetadataJson = metadata(row);
        stagedAgeing.save(item);
        stageUnitCandidate(session, file, rowNumber, item.unitCode, unitIndex);
    }

    private SmartStagedProduct stageProductCandidate(
        ImportSession session, ImportSessionFile file, DomainEnums.DetectedFileType type, int rowNumber,
        Map<String, String> row, Map<String, SmartStagedProduct> index
    ) {
        var rawName = lookup(row, "Item Name", "Product Name", "Stock Item", "Name", "STOCKITEMNAME");
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        var unitCode = normalizeUnit(lookup(row, "Unit", "Unit Code", "Base Unit", "BASEUNITS"));
        var key = productKey(normalizeName(rawName), unitCode);
        var existing = index.get(key);
        if (existing != null) {
            return existing;
        }
        var product = new SmartStagedProduct();
        own(product, session, file, rowNumber);
        product.sourceType = type;
        product.rawName = rawName.trim();
        product.normalizedName = normalizeName(rawName);
        product.unitCode = unitCode;
        product.sku = blankToNull(lookup(row, "SKU", "Stock Keeping Unit", "Item Code"));
        product.barcode = blankToNull(lookup(row, "Barcode", "EAN", "UPC"));
        product.categoryName = blankToNull(lookup(row, "Category", "Stock Group", "Parent"));
        product.brandName = blankToNull(lookup(row, "Brand"));
        product.hsn = blankToNull(lookup(row, "HSN", "HSN Code", "HSNCODE"));
        product.gstPercent = decimal(lookup(row, "GST %", "GST Percentage", "Tax Rate"));
        product.purchasePrice = decimal(lookup(row, "Purchase Price", "Opening Rate", "Rate"));
        product.externalId = blankToNull(lookup(row, "Tally GUID", "GUID", "MASTERID", "Master ID", "Tally Master ID"));
        product.rawMetadataJson = metadata(row);
        stagedProducts.save(product);
        index.put(key, product);
        return product;
    }

    private SmartStagedParty stagePartyCandidate(
        ImportSession session, ImportSessionFile file, DomainEnums.DetectedFileType sourceType, int rowNumber, Map<String, String> row,
        DomainEnums.SmartPartyType partyType, Map<String, SmartStagedParty> index
    ) {
        var rawName = lookup(row, "Party Name", "Customer", "Supplier", "Ledger Name", "LEDGERNAME", "name");
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        var gstin = blankToNull(lookup(row, "GSTIN", "GST No", "GST Number", "PARTYGSTIN"));
        var key = partyType + "|" + normalizeName(rawName) + "|" + normalize(gstin);
        var existing = index.get(key);
        if (existing != null) {
            if (sourceType == DomainEnums.DetectedFileType.DEBTOR_CREDITOR_ANALYSIS) {
                existing.sourceType = sourceType;
                existing.openingBalance = decimal(lookup(row, "Opening Balance", "Outstanding", "Balance"));
                var parsedSnapshotDate = date(lookup(row, "Snapshot Date", "As On Date", "Date"));
                existing.snapshotDate = parsedSnapshotDate == null ? file.dateRangeEnd : parsedSnapshotDate;
                existing.rawMetadataJson.putAll(metadata(row));
                stagedParties.save(existing);
            }
            return existing;
        }
        var party = new SmartStagedParty();
        own(party, session, file, rowNumber);
        party.sourceType = sourceType;
        party.partyType = partyType;
        party.rawName = rawName.trim();
        party.normalizedName = normalizeName(rawName);
        party.gstin = gstin;
        party.phone = blankToNull(lookup(row, "Phone", "Mobile", "Phone Number"));
        party.email = blankToNull(lookup(row, "Email", "Email Address"));
        party.openingBalance = decimal(lookup(row, "Opening Balance", "Outstanding", "Balance"));
        var parsedSnapshotDate = date(lookup(row, "Snapshot Date", "As On Date", "Date"));
        party.snapshotDate = parsedSnapshotDate == null ? file.dateRangeEnd : parsedSnapshotDate;
        party.rawMetadataJson = metadata(row);
        stagedParties.save(party);
        index.put(key, party);
        return party;
    }

    private SmartStagedWarehouse stageWarehouseCandidate(
        ImportSession session, ImportSessionFile file, int rowNumber, String rawName, Map<String, SmartStagedWarehouse> index
    ) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        var normalized = normalizeName(rawName);
        var existing = index.get(normalized);
        if (existing != null) {
            return existing;
        }
        var warehouse = new SmartStagedWarehouse();
        own(warehouse, session, file, rowNumber);
        warehouse.sourceWarehouseName = rawName.trim();
        warehouse.normalizedName = normalized;
        stagedWarehouses.save(warehouse);
        index.put(normalized, warehouse);
        return warehouse;
    }

    private SmartStagedUnit stageUnitCandidate(
        ImportSession session, ImportSessionFile file, int rowNumber, String rawCode, Map<String, SmartStagedUnit> index
    ) {
        var normalized = normalizeUnit(rawCode);
        if (normalized == null) {
            return null;
        }
        var existing = index.get(normalized);
        if (existing != null) {
            return existing;
        }
        var unit = new SmartStagedUnit();
        own(unit, session, file, rowNumber);
        unit.sourceUnitCode = rawCode.trim();
        unit.normalizedCode = normalized;
        stagedUnits.save(unit);
        index.put(normalized, unit);
        return unit;
    }

    private void matchCanonicalRecords(ImportSession session) {
        var tenantId = session.tenantId;
        var resolutionMap = resolutions.findByTenantIdAndImportSessionId(tenantId, session.id).stream()
            .collect(java.util.stream.Collectors.toMap(row -> row.resolutionKey, row -> row, (left, right) -> right));
        var existingProducts = products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
        var existingWarehouses = warehouses.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);

        var unitsForSession = stagedUnits.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, session.id);
        for (var row : unitsForSession) {
            var match = units.findByTenantIdAndCodeIgnoreCase(tenantId, row.normalizedCode);
            row.matchedUnitId = match.map(unit -> unit.id).orElse(null);
            row.matchStatus = match.isPresent() ? DomainEnums.SmartMatchStatus.MATCH_EXISTING : DomainEnums.SmartMatchStatus.CREATE_NEW;
            row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
        }
        stagedUnits.saveAll(unitsForSession);

        var productsForSession = stagedProducts.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, session.id);
        for (var row : productsForSession) {
            matchProduct(tenantId, row, existingProducts, resolutionMap.get(productResolutionKey(row.normalizedName, row.unitCode)));
        }
        stagedProducts.saveAll(productsForSession);
        var productIndex = productsForSession.stream().collect(java.util.stream.Collectors.toMap(
            row -> productKey(row.normalizedName, row.unitCode), row -> row, (left, right) -> left, LinkedHashMap::new));

        var partiesForSession = stagedParties.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, session.id);
        for (var row : partiesForSession) {
            matchParty(tenantId, row);
        }
        stagedParties.saveAll(partiesForSession);

        var warehousesForSession = stagedWarehouses.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(tenantId, session.id);
        for (var row : warehousesForSession) {
            matchWarehouse(tenantId, row, existingWarehouses, resolutionMap.get(warehouseResolutionKey(row.normalizedName)));
        }
        stagedWarehouses.saveAll(warehousesForSession);
        var warehouseIndex = warehousesForSession.stream().collect(java.util.stream.Collectors.toMap(
            row -> row.normalizedName, row -> row, (left, right) -> left, LinkedHashMap::new));

        matchSnapshots(session, productIndex, warehouseIndex);
        matchVouchers(session, productIndex, partiesForSession, warehouseIndex);
        matchCashbook(session, partiesForSession);
        matchAgeing(session, productIndex);
    }

    private void matchProduct(
        UUID tenantId,
        SmartStagedProduct row,
        List<Product> existingProducts,
        SmartImportResolution resolution
    ) {
        if (row.normalizedName == null || row.normalizedName.isBlank()) {
            row.matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
            row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
            return;
        }
        if (resolution != null && "MAP_PRODUCT".equals(resolution.action) && resolution.targetId != null) {
            var target = products.findByTenantIdAndId(tenantId, resolution.targetId).orElse(null);
            if (target != null) {
                productMatched(row, target, DomainEnums.SmartReviewStatus.RESOLVED);
                return;
            }
        }
        if (resolution != null && "CREATE_PRODUCT_LATER".equals(resolution.action)) {
            row.matchStatus = DomainEnums.SmartMatchStatus.CREATE_NEW;
            row.matchedProductId = null;
            row.reviewStatus = DomainEnums.SmartReviewStatus.RESOLVED;
            return;
        }

        var external = findByExternalId(existingProducts, row.externalId);
        if (external != null) {
            productMatched(row, external, DomainEnums.SmartReviewStatus.AUTO_RESOLVED);
            return;
        }
        if (row.sku != null) {
            var match = products.findByTenantIdAndSkuIgnoreCase(tenantId, row.sku).orElse(null);
            if (match != null) {
                productMatched(row, match, DomainEnums.SmartReviewStatus.AUTO_RESOLVED);
                return;
            }
        }
        if (row.barcode != null) {
            var barcode = barcodes.findByTenantIdAndBarcode(tenantId, row.barcode).orElse(null);
            if (barcode != null) {
                var match = products.findByTenantIdAndId(tenantId, barcode.productId).orElse(null);
                if (match != null) {
                    productMatched(row, match, DomainEnums.SmartReviewStatus.AUTO_RESOLVED);
                    return;
                }
            }
        }
        if (row.unitCode != null) {
            var unit = units.findByTenantIdAndCodeIgnoreCase(tenantId, row.unitCode).orElse(null);
            if (unit != null) {
                var match = products.findByTenantIdAndNormalizedNameIgnoreCaseAndBaseUnitId(tenantId, row.normalizedName, unit.id).orElse(null);
                if (match != null) {
                    productMatched(row, match, DomainEnums.SmartReviewStatus.AUTO_RESOLVED);
                    return;
                }
            }
        }

        var candidates = possibleProducts(tenantId, existingProducts, row.normalizedName, row.unitCode);
        if (candidates.size() > 1) {
            row.matchStatus = DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW;
            row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        } else if (candidates.size() == 1) {
            row.matchStatus = DomainEnums.SmartMatchStatus.POSSIBLE_DUPLICATE_REVIEW;
            row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        } else if (row.unitCode == null) {
            row.matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
            row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        } else {
            row.matchStatus = DomainEnums.SmartMatchStatus.CREATE_NEW;
            row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
        }
    }

    private void matchParty(UUID tenantId, SmartStagedParty row) {
        var customerMatches = new LinkedHashMap<UUID, Customer>();
        var supplierMatches = new LinkedHashMap<UUID, Supplier>();
        if (row.gstin != null) {
            customers.findByTenantIdAndGstinIgnoreCase(tenantId, row.gstin).ifPresent(match -> customerMatches.put(match.id, match));
            suppliers.findByTenantIdAndGstinIgnoreCase(tenantId, row.gstin).ifPresent(match -> supplierMatches.put(match.id, match));
        }
        if (row.normalizedName != null) {
            customers.findByTenantIdAndNameIgnoreCase(tenantId, row.normalizedName).ifPresent(match -> customerMatches.put(match.id, match));
            suppliers.findByTenantIdAndNameIgnoreCase(tenantId, row.normalizedName).ifPresent(match -> supplierMatches.put(match.id, match));
        }
        if (row.phone != null) {
            customers.findByTenantIdAndPhone(tenantId, row.phone).forEach(match -> customerMatches.put(match.id, match));
            suppliers.findByTenantIdAndPhone(tenantId, row.phone).forEach(match -> supplierMatches.put(match.id, match));
        }
        if (row.email != null) {
            customers.findByTenantIdAndEmailIgnoreCase(tenantId, row.email).forEach(match -> customerMatches.put(match.id, match));
            suppliers.findByTenantIdAndEmailIgnoreCase(tenantId, row.email).forEach(match -> supplierMatches.put(match.id, match));
        }

        if (row.partyType == DomainEnums.SmartPartyType.CUSTOMER) {
            supplierMatches.clear();
        } else if (row.partyType == DomainEnums.SmartPartyType.SUPPLIER) {
            customerMatches.clear();
        }
        var total = customerMatches.size() + supplierMatches.size();
        if (total == 1) {
            row.matchedCustomerId = customerMatches.isEmpty() ? null : customerMatches.keySet().iterator().next();
            row.matchedSupplierId = supplierMatches.isEmpty() ? null : supplierMatches.keySet().iterator().next();
            row.matchStatus = DomainEnums.SmartMatchStatus.MATCH_EXISTING;
            row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
        } else if (total > 1) {
            row.matchStatus = DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW;
            row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        } else if (row.normalizedName == null || row.partyType == DomainEnums.SmartPartyType.UNKNOWN) {
            row.matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
            row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        } else {
            row.matchStatus = DomainEnums.SmartMatchStatus.CREATE_NEW;
            row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
        }
    }

    private void matchWarehouse(
        UUID tenantId,
        SmartStagedWarehouse row,
        List<Warehouse> existingWarehouses,
        SmartImportResolution resolution
    ) {
        if (resolution != null && "MAP_WAREHOUSE".equals(resolution.action) && resolution.targetId != null) {
            var target = warehouses.findByTenantIdAndId(tenantId, resolution.targetId).orElse(null);
            if (target != null) {
                warehouseMatched(row, target.id, DomainEnums.SmartReviewStatus.RESOLVED);
                return;
            }
        }
        if (resolution != null && "CREATE_WAREHOUSE_LATER".equals(resolution.action)) {
            row.matchStatus = DomainEnums.SmartMatchStatus.CREATE_NEW;
            row.matchedWarehouseId = null;
            row.reviewStatus = DomainEnums.SmartReviewStatus.RESOLVED;
            return;
        }
        var match = existingWarehouses.stream().filter(candidate -> normalizeName(candidate.name).equals(row.normalizedName)).findFirst().orElse(null);
        if (match != null) {
            warehouseMatched(row, match.id, DomainEnums.SmartReviewStatus.AUTO_RESOLVED);
        } else {
            row.matchStatus = DomainEnums.SmartMatchStatus.CREATE_NEW;
            row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        }
    }

    private void matchSnapshots(
        ImportSession session,
        Map<String, SmartStagedProduct> productIndex,
        Map<String, SmartStagedWarehouse> warehouseIndex
    ) {
        var rows = stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        for (var row : rows) {
            var product = productIndex.get(productKey(row.normalizedProductName, row.unitCode));
            var warehouse = row.warehouseName == null ? null : warehouseIndex.get(normalizeName(row.warehouseName));
            row.matchedProductId = product == null ? null : product.matchedProductId;
            row.matchedWarehouseId = warehouse == null ? null : warehouse.matchedWarehouseId;
            row.matchStatus = product == null ? DomainEnums.SmartMatchStatus.UNKNOWN : product.matchStatus;
            if (product == null || product.reviewStatus == DomainEnums.SmartReviewStatus.PENDING
                || (warehouse != null && warehouse.reviewStatus == DomainEnums.SmartReviewStatus.PENDING)
                || row.importedStock.signum() < 0) {
                row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
                row.action = DomainEnums.SmartSnapshotAction.REVIEW_REQUIRED;
                row.currentStock = row.matchedProductId == null ? ZERO : stockLedger.currentStock(session.tenantId, row.matchedProductId, row.matchedWarehouseId);
                row.deltaPreview = row.importedStock.subtract(row.currentStock);
                continue;
            }
            row.currentStock = row.matchedProductId == null ? ZERO : stockLedger.currentStock(session.tenantId, row.matchedProductId, row.matchedWarehouseId);
            row.deltaPreview = row.importedStock.subtract(row.currentStock);
            row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
            if (row.matchedProductId == null) {
                row.action = DomainEnums.SmartSnapshotAction.CREATE_NEW_PRODUCT;
            } else if (row.deltaPreview.compareTo(ZERO) == 0) {
                row.action = DomainEnums.SmartSnapshotAction.NO_CHANGE;
            } else {
                row.action = DomainEnums.SmartSnapshotAction.CREATE_SNAPSHOT_ADJUSTMENT;
            }
        }
        stagedSnapshots.saveAll(rows);
    }

    private void matchVouchers(
        ImportSession session,
        Map<String, SmartStagedProduct> productIndex,
        List<SmartStagedParty> partiesForSession,
        Map<String, SmartStagedWarehouse> warehouseIndex
    ) {
        var snapshots = stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var latestSnapshotDate = snapshots.stream().map(row -> row.snapshotDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        var hasSnapshot = !snapshots.isEmpty();
        var seenFingerprints = new HashSet<String>();
        var vouchers = stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(session.tenantId, session.id);
        for (var row : vouchers) {
            var party = partiesForSession.stream().filter(candidate -> Objects.equals(candidate.normalizedName, row.normalizedPartyName)).findFirst().orElse(null);
            row.matchedPartyId = party == null ? null : firstNonNull(party.matchedCustomerId, party.matchedSupplierId);
            row.stockImpactModeSuggestion = !hasSnapshot
                ? DomainEnums.SmartVoucherStockImpactSuggestion.CREATE_INVOICES_AND_STOCK_MOVEMENTS
                : latestSnapshotDate != null && row.voucherDate != null && row.voucherDate.isAfter(latestSnapshotDate)
                    ? DomainEnums.SmartVoucherStockImpactSuggestion.APPLY_AFTER_SNAPSHOT_DATE
                    : DomainEnums.SmartVoucherStockImpactSuggestion.CREATE_INVOICES_ONLY;
            var repeatedInSession = row.fingerprint != null && !seenFingerprints.add(row.fingerprint);
            var existingStatus = existingVoucherStatus(session.tenantId, row);
            if (repeatedInSession || existingStatus == ExistingVoucherStatus.EXACT_DUPLICATE) {
                row.matchStatus = DomainEnums.SmartMatchStatus.SKIP_DUPLICATE;
                row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
            } else if (existingStatus == ExistingVoucherStatus.AMBIGUOUS_CONFLICT) {
                row.matchStatus = DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW;
                row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
            } else if (row.voucherNumber == null || row.voucherDate == null || row.partyName == null) {
                row.matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
                row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
            } else if (party != null && party.reviewStatus == DomainEnums.SmartReviewStatus.PENDING) {
                row.matchStatus = DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW;
                row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
            } else {
                row.matchStatus = DomainEnums.SmartMatchStatus.CREATE_NEW;
                row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
            }
        }
        stagedVouchers.saveAll(vouchers);

        var items = stagedVoucherItems.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        for (var row : items) {
            var product = productIndex.get(productKey(row.normalizedProductName, row.unitCode));
            var warehouse = row.warehouseName == null ? null : warehouseIndex.get(normalizeName(row.warehouseName));
            row.matchedProductId = product == null ? null : product.matchedProductId;
            row.matchedWarehouseId = warehouse == null ? null : warehouse.matchedWarehouseId;
            row.matchStatus = product == null ? DomainEnums.SmartMatchStatus.UNKNOWN : product.matchStatus;
            row.reviewStatus = product == null || product.reviewStatus == DomainEnums.SmartReviewStatus.PENDING
                || (warehouse != null && warehouse.reviewStatus == DomainEnums.SmartReviewStatus.PENDING)
                ? DomainEnums.SmartReviewStatus.PENDING : DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
        }
        stagedVoucherItems.saveAll(items);
    }

    private void matchCashbook(ImportSession session, List<SmartStagedParty> partiesForSession) {
        var rows = stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(session.tenantId, session.id);
        var sessionVouchers = stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(session.tenantId, session.id);
        for (var row : rows) {
            resetCashbookMatch(row);
            if (row.entryDate == null || row.amount == null || row.amount.signum() <= 0
                || row.direction == DomainEnums.SmartCashbookDirection.UNKNOWN) {
                cashbookReview(row, DomainEnums.CashbookMatchStatus.UNMATCHED_REVIEW,
                    "A payment date, positive amount, and receipt/payment direction are required.");
                continue;
            }

            var invoiceMatch = matchInvoiceByReference(session.tenantId, row, sessionVouchers);
            if (invoiceMatch != null) {
                applyInvoiceMatch(row, invoiceMatch, "Invoice reference matched exactly.", new BigDecimal("1.00"));
                continue;
            }

            var partyMatches = cashbookPartyMatches(session.tenantId, row, partiesForSession);
            if (partyMatches.size() > 1) {
                cashbookReview(row, DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW,
                    "More than one party matches this cashbook row.");
                continue;
            }
            if (partyMatches.isEmpty()) {
                cashbookReview(row, DomainEnums.CashbookMatchStatus.UNMATCHED_REVIEW,
                    "No exact tenant party match was found.");
                continue;
            }

            var party = partyMatches.getFirst();
            row.matchedPartyId = party.id;
            row.matchedPartyType = party.type;
            var amountMatches = matchInvoicesByPartyAmountAndDate(session.tenantId, row, party, sessionVouchers);
            if (amountMatches.size() == 1) {
                applyInvoiceMatch(row, amountMatches.getFirst(),
                    "One invoice matched the exact party and amount within seven days.", new BigDecimal("0.90"));
            } else if (amountMatches.size() > 1) {
                cashbookReview(row, DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW,
                    "Multiple invoices match the party, amount, and date window.");
            } else {
                row.cashbookMatchStatus = party.type == DomainEnums.SmartPartyType.CUSTOMER
                    ? DomainEnums.CashbookMatchStatus.MATCHED_CUSTOMER_PAYMENT
                    : DomainEnums.CashbookMatchStatus.MATCHED_SUPPLIER_PAYMENT;
                row.matchStatus = DomainEnums.SmartMatchStatus.MATCH_EXISTING;
                row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
                row.matchConfidence = new BigDecimal("0.80");
                row.matchReason = "Exact tenant party matched; no invoice was linked.";
            }
        }
        stagedCashbook.saveAll(rows);
    }

    private void resetCashbookMatch(SmartStagedCashbookEntry row) {
        row.matchedPartyId = null;
        row.matchedInvoiceId = null;
        row.matchedPartyType = DomainEnums.SmartPartyType.UNKNOWN;
        row.cashbookMatchStatus = DomainEnums.CashbookMatchStatus.UNMATCHED_REVIEW;
        row.matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
        row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        row.matchConfidence = ZERO;
        row.matchReason = null;
    }

    private void cashbookReview(
        SmartStagedCashbookEntry row, DomainEnums.CashbookMatchStatus status, String reason
    ) {
        row.cashbookMatchStatus = status;
        row.matchStatus = status == DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW
            ? DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW : DomainEnums.SmartMatchStatus.UNKNOWN;
        row.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        row.matchConfidence = status == DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW
            ? new BigDecimal("0.50") : ZERO;
        row.matchReason = reason;
    }

    private List<CashbookPartyMatch> cashbookPartyMatches(
        UUID tenantId, SmartStagedCashbookEntry row, List<SmartStagedParty> staged
    ) {
        var expectedType = row.direction == DomainEnums.SmartCashbookDirection.RECEIPT
            ? DomainEnums.SmartPartyType.CUSTOMER : DomainEnums.SmartPartyType.SUPPLIER;
        var matches = new LinkedHashMap<String, CashbookPartyMatch>();
        for (var candidate : staged) {
            if (candidate.partyType != expectedType || candidate.reviewStatus == DomainEnums.SmartReviewStatus.PENDING) continue;
            var gstinMatch = row.gstin != null && candidate.gstin != null && normalize(row.gstin).equals(normalize(candidate.gstin));
            var nameMatch = !cashOrBankLedger(row.partyName) && Objects.equals(candidate.normalizedName, normalizeName(row.partyName));
            if (gstinMatch || nameMatch) {
                var id = expectedType == DomainEnums.SmartPartyType.CUSTOMER ? candidate.matchedCustomerId : candidate.matchedSupplierId;
                matches.put(expectedType + "|" + firstNonBlank(id == null ? null : id.toString(), candidate.normalizedName),
                    new CashbookPartyMatch(expectedType, id, candidate.normalizedName));
            }
        }
        if (expectedType == DomainEnums.SmartPartyType.CUSTOMER) {
            if (row.gstin != null) customers.findByTenantIdAndGstinIgnoreCase(tenantId, row.gstin)
                .ifPresent(value -> matches.put("CUSTOMER|" + value.id, new CashbookPartyMatch(expectedType, value.id, normalizeName(value.name))));
            if (!cashOrBankLedger(row.partyName)) customers.findByTenantIdAndNameIgnoreCase(tenantId, normalizeName(row.partyName))
                .ifPresent(value -> matches.put("CUSTOMER|" + value.id, new CashbookPartyMatch(expectedType, value.id, normalizeName(value.name))));
        } else {
            if (row.gstin != null) suppliers.findByTenantIdAndGstinIgnoreCase(tenantId, row.gstin)
                .ifPresent(value -> matches.put("SUPPLIER|" + value.id, new CashbookPartyMatch(expectedType, value.id, normalizeName(value.name))));
            if (!cashOrBankLedger(row.partyName)) suppliers.findByTenantIdAndNameIgnoreCase(tenantId, normalizeName(row.partyName))
                .ifPresent(value -> matches.put("SUPPLIER|" + value.id, new CashbookPartyMatch(expectedType, value.id, normalizeName(value.name))));
        }
        return new ArrayList<>(matches.values());
    }

    private CashbookInvoiceMatch matchInvoiceByReference(
        UUID tenantId, SmartStagedCashbookEntry row, List<SmartStagedVoucher> staged
    ) {
        if (row.referenceNumber == null || row.referenceNumber.isBlank()) return null;
        if (row.direction == DomainEnums.SmartCashbookDirection.RECEIPT) {
            var existing = salesInvoices.findByTenantIdAndInvoiceNumberIgnoreCase(tenantId, row.referenceNumber).orElse(null);
            if (existing != null) return new CashbookInvoiceMatch(DomainEnums.SmartPartyType.CUSTOMER, existing.customerId,
                existing.id, existing.invoiceNumber, existing.invoiceDate, existing.totalAmount);
        } else {
            var existing = purchaseInvoices.findByTenantIdAndInvoiceNumberIgnoreCase(tenantId, row.referenceNumber).orElse(null);
            if (existing != null) return new CashbookInvoiceMatch(DomainEnums.SmartPartyType.SUPPLIER, existing.supplierId,
                existing.id, existing.invoiceNumber, existing.invoiceDate, existing.totalAmount);
        }
        return staged.stream().filter(voucher -> row.referenceNumber.equalsIgnoreCase(nullSafe(voucher.voucherNumber)))
            .filter(voucher -> row.direction == DomainEnums.SmartCashbookDirection.RECEIPT
                ? voucherKindForCashbook(voucher.voucherType) == DomainEnums.SmartPartyType.CUSTOMER
                : voucherKindForCashbook(voucher.voucherType) == DomainEnums.SmartPartyType.SUPPLIER)
            .findFirst().map(voucher -> new CashbookInvoiceMatch(voucherKindForCashbook(voucher.voucherType),
                voucher.matchedPartyId, null, voucher.voucherNumber, voucher.voucherDate, voucher.totalAmount)).orElse(null);
    }

    private List<CashbookInvoiceMatch> matchInvoicesByPartyAmountAndDate(
        UUID tenantId, SmartStagedCashbookEntry row, CashbookPartyMatch party, List<SmartStagedVoucher> staged
    ) {
        var matches = new LinkedHashMap<String, CashbookInvoiceMatch>();
        if (party.id != null && party.type == DomainEnums.SmartPartyType.CUSTOMER) {
            for (var invoice : salesInvoices.findByTenantIdAndCustomerId(tenantId, party.id)) {
                if (sameCashbookAmountAndDate(row, invoice.totalAmount, invoice.invoiceDate)) {
                    matches.put("SALES|" + invoice.id, new CashbookInvoiceMatch(party.type, party.id, invoice.id,
                        invoice.invoiceNumber, invoice.invoiceDate, invoice.totalAmount));
                }
            }
        } else if (party.id != null) {
            for (var invoice : purchaseInvoices.findByTenantIdAndSupplierId(tenantId, party.id)) {
                if (sameCashbookAmountAndDate(row, invoice.totalAmount, invoice.invoiceDate)) {
                    matches.put("PURCHASE|" + invoice.id, new CashbookInvoiceMatch(party.type, party.id, invoice.id,
                        invoice.invoiceNumber, invoice.invoiceDate, invoice.totalAmount));
                }
            }
        }
        for (var voucher : staged) {
            if (voucherKindForCashbook(voucher.voucherType) != party.type
                || !Objects.equals(normalizeName(voucher.partyName), party.normalizedName)
                || !sameCashbookAmountAndDate(row, voucher.totalAmount, voucher.voucherDate)) continue;
            matches.put("STAGED|" + voucher.id, new CashbookInvoiceMatch(party.type, party.id, null,
                voucher.voucherNumber, voucher.voucherDate, voucher.totalAmount));
        }
        return new ArrayList<>(matches.values());
    }

    private boolean sameCashbookAmountAndDate(SmartStagedCashbookEntry row, BigDecimal amount, LocalDate invoiceDate) {
        return row.entryDate != null && invoiceDate != null && nvl(row.amount).compareTo(nvl(amount).abs()) == 0
            && Math.abs(java.time.temporal.ChronoUnit.DAYS.between(invoiceDate, row.entryDate)) <= 7;
    }

    private void applyInvoiceMatch(
        SmartStagedCashbookEntry row, CashbookInvoiceMatch match, String reason, BigDecimal confidence
    ) {
        row.matchedPartyId = match.partyId;
        row.matchedInvoiceId = match.invoiceId;
        row.matchedPartyType = match.partyType;
        row.cashbookMatchStatus = match.partyType == DomainEnums.SmartPartyType.CUSTOMER
            ? DomainEnums.CashbookMatchStatus.MATCHED_SALES_INVOICE
            : DomainEnums.CashbookMatchStatus.MATCHED_PURCHASE_INVOICE;
        row.matchStatus = DomainEnums.SmartMatchStatus.MATCH_EXISTING;
        row.reviewStatus = DomainEnums.SmartReviewStatus.AUTO_RESOLVED;
        row.matchConfidence = confidence;
        row.matchReason = reason;
        if (row.referenceNumber == null) row.referenceNumber = match.invoiceNumber;
    }

    private DomainEnums.SmartPartyType voucherKindForCashbook(String voucherType) {
        var normalized = normalize(voucherType);
        if (normalized.contains("SALE")) return DomainEnums.SmartPartyType.CUSTOMER;
        if (normalized.contains("PURCHASE")) return DomainEnums.SmartPartyType.SUPPLIER;
        return DomainEnums.SmartPartyType.UNKNOWN;
    }

    private boolean cashOrBankLedger(String name) {
        var normalized = normalizeName(name);
        return normalized.equals("CASH") || normalized.equals("BANK") || normalized.contains("CASH IN HAND")
            || normalized.contains("BANK ACCOUNT");
    }

    private void matchAgeing(ImportSession session, Map<String, SmartStagedProduct> productIndex) {
        var rows = stagedAgeing.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        for (var row : rows) {
            var product = productIndex.get(productKey(row.normalizedProductName, row.unitCode));
            row.matchedProductId = product == null ? null : product.matchedProductId;
            row.matchStatus = product == null ? DomainEnums.SmartMatchStatus.UNKNOWN : product.matchStatus;
            row.reviewStatus = product != null && product.reviewStatus != DomainEnums.SmartReviewStatus.PENDING
                ? DomainEnums.SmartReviewStatus.AUTO_RESOLVED : DomainEnums.SmartReviewStatus.PENDING;
        }
        stagedAgeing.saveAll(rows);
    }

    private List<ImportPlanIssue> buildReviewItems(ImportSession session, List<ParseFailure> parseFailures) {
        var generated = new ArrayList<ImportPlanIssue>();
        var existingProducts = products.findByTenantIdAndActiveTrueOrderByNameAsc(session.tenantId);
        var existingWarehouses = warehouses.findByTenantIdAndActiveTrueOrderByNameAsc(session.tenantId);
        var resolutionMap = resolutions.findByTenantIdAndImportSessionId(session.tenantId, session.id).stream()
            .collect(java.util.stream.Collectors.toMap(row -> row.resolutionKey, row -> row, (left, right) -> right));

        var phaseIssues = issues.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(session.tenantId, session.id);
        for (var issue : phaseIssues) {
            if ("UNKNOWN_FILE_TYPE".equals(issue.code) || "UNCERTAIN_FILE_CLASSIFICATION".equals(issue.code)) {
                issue.availableChoices = fileTypeChoices();
                issue.contextJson.put("resolutionKey", "FILE_TYPE:" + issue.affectedFileId);
                issue.contextJson.put("entityType", "FILE");
            }
        }
        issues.saveAll(phaseIssues);

        for (var failure : parseFailures) {
            generated.add(reviewIssue(session, failure.file.id, 0, DomainEnums.ImportPlanIssueSeverity.ERROR,
                "STAGING_PARSE_FAILED", "The classified file could not be parsed into canonical staging: " + failure.message,
                "Correct the source file or override its file type, then rebuild staging.",
                fileTypeChoices(), Map.of("resolutionKey", "FILE_TYPE:" + failure.file.id, "entityType", "FILE")));
        }

        for (var row : stagedProducts.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id)) {
            if (row.reviewStatus != DomainEnums.SmartReviewStatus.PENDING) continue;
            var key = productResolutionKey(row.normalizedName, row.unitCode);
            if (ignored(resolutionMap, key)) continue;
            var candidates = possibleProducts(session.tenantId, existingProducts, row.normalizedName, row.unitCode);
            var choices = candidates.stream().limit(10).map(candidate -> choice("MAP_PRODUCT", "Map to " + candidate.name, candidate.id)).toList();
            var mutableChoices = new ArrayList<Map<String, Object>>(choices);
            mutableChoices.add(choice("CREATE_PRODUCT_LATER", "Create as a new product later", null));
            var code = row.matchStatus == DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW ? "AMBIGUOUS_PRODUCT_MATCH"
                : row.matchStatus == DomainEnums.SmartMatchStatus.POSSIBLE_DUPLICATE_REVIEW ? "POSSIBLE_DUPLICATE_PRODUCT"
                : "MISSING_PRODUCT_IDENTITY";
            generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.REVIEW_REQUIRED,
                code, "Product requires review: " + nullSafe(row.rawName),
                "Map it to an existing product or confirm that a new product should be created in a later phase.", mutableChoices,
                Map.of("resolutionKey", key, "entityType", "PRODUCT", "stagingId", row.id.toString())));
        }

        for (var row : stagedParties.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id)) {
            if (row.reviewStatus != DomainEnums.SmartReviewStatus.PENDING) continue;
            var key = "PARTY:" + row.partyType + "|" + nullSafe(row.normalizedName);
            if (ignored(resolutionMap, key)) continue;
            var code = row.matchStatus == DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW ? "AMBIGUOUS_PARTY" : "UNKNOWN_PARTY";
            generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.REVIEW_REQUIRED,
                code, "Party requires review: " + nullSafe(row.rawName),
                "Confirm the party type and identity before final commit is introduced.", List.of(choice("IGNORE_WARNING", "Acknowledge for this planning session", null)),
                Map.of("resolutionKey", key, "entityType", "PARTY", "stagingId", row.id.toString())));
        }

        for (var row : stagedWarehouses.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id)) {
            if (row.reviewStatus != DomainEnums.SmartReviewStatus.PENDING) continue;
            var key = warehouseResolutionKey(row.normalizedName);
            if (ignored(resolutionMap, key)) continue;
            var choices = new ArrayList<Map<String, Object>>();
            existingWarehouses.stream().limit(20).forEach(warehouse -> choices.add(choice("MAP_WAREHOUSE", "Map to " + warehouse.name, warehouse.id)));
            choices.add(choice("CREATE_WAREHOUSE_LATER", "Create this warehouse later", null));
            generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.REVIEW_REQUIRED,
                "UNKNOWN_WAREHOUSE", "Warehouse/godown is not mapped: " + nullSafe(row.sourceWarehouseName),
                "Map it to an existing warehouse or mark it for creation in the commit phase.", choices,
                Map.of("resolutionKey", key, "entityType", "WAREHOUSE", "stagingId", row.id.toString())));
        }

        for (var row : stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id)) {
            if (row.importedStock.signum() < 0) {
                generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.ERROR,
                    "NEGATIVE_STOCK", "Imported snapshot quantity is negative for " + nullSafe(row.productName),
                    "Review the Tally quantity and tenant negative-stock policy before commit is implemented.", List.of(),
                    Map.of("resolutionKey", "SNAPSHOT_NEGATIVE:" + row.id, "entityType", "STOCK_SNAPSHOT", "stagingId", row.id.toString())));
            }
        }

        for (var row : stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(session.tenantId, session.id)) {
            if (row.matchStatus == DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW) {
                var key = "VOUCHER_DUPLICATE:" + row.fingerprint;
                if (!ignored(resolutionMap, key)) {
                    generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.REVIEW_REQUIRED,
                        "DUPLICATE_VOUCHER_CONFLICT", "Voucher number exists with different date, party, or amount: " + nullSafe(row.voucherNumber),
                        "Review the conflicting voucher identity before commit.", List.of(choice("IGNORE_WARNING", "Keep this voucher blocked", null)),
                        Map.of("resolutionKey", key, "entityType", "VOUCHER", "stagingId", row.id.toString())));
                }
            } else if (row.matchStatus == DomainEnums.SmartMatchStatus.UNKNOWN) {
                generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.ERROR,
                    "MISSING_REQUIRED_IDENTITY", "Voucher is missing number, date, or party identity.",
                    "Correct the source export or file classification before commit.", List.of(),
                    Map.of("resolutionKey", "VOUCHER_IDENTITY:" + row.id, "entityType", "VOUCHER", "stagingId", row.id.toString())));
            }
        }

        for (var row : stagedVoucherItems.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id)) {
            if ("ZERO_COST_ITEM".equalsIgnoreCase(row.rateSource) || (row.rate != null && row.rate.signum() == 0)) {
                var key = "ZERO_RATE:" + row.id;
                if (!ignored(resolutionMap, key)) {
                    generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.WARNING,
                        "ZERO_RATE_ITEM", "Voucher item has a zero rate: " + nullSafe(row.productName),
                        "Confirm whether this is a free, scheme, or sample item.", List.of(choice("IGNORE_WARNING", "Confirm zero-rate item", null)),
                        Map.of("resolutionKey", key, "entityType", "VOUCHER_ITEM", "stagingId", row.id.toString())));
                }
            } else if (row.rate == null || row.rate.signum() < 0 || "INVALID".equalsIgnoreCase(row.rateSource)) {
                generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.ERROR,
                    "INVALID_RATE", "Voucher item has an invalid rate: " + nullSafe(row.productName),
                    "Correct the source rate or provide enough amount/quantity information for derivation.", List.of(),
                    Map.of("resolutionKey", "INVALID_RATE:" + row.id, "entityType", "VOUCHER_ITEM", "stagingId", row.id.toString())));
            }
        }

        for (var row : stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(session.tenantId, session.id)) {
            if (row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.UNMATCHED_REVIEW
                || row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW) {
                var key = "CASHBOOK:" + row.id;
                if (!ignored(resolutionMap, key)) {
                    generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.WARNING,
                        row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW
                            ? "CASHBOOK_LOW_CONFIDENCE" : "CASHBOOK_UNMATCHED_ENTRY",
                        "Cashbook entry needs review: " + nullSafe(row.partyName),
                        "Low-confidence entries are kept for review and are not posted as payments.",
                        List.of(choice("IGNORE_WARNING", "Keep in the review queue without posting", null)),
                        Map.of("resolutionKey", key, "entityType", "CASHBOOK", "stagingId", row.id.toString())));
                }
            }
        }

        for (var row : stagedAgeing.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id)) {
            if (row.matchedProductId == null) {
                var key = "AGEING:" + row.id;
                if (!ignored(resolutionMap, key)) {
                    generated.add(reviewIssue(session, row.sessionFileId, row.sourceRowNumber, DomainEnums.ImportPlanIssueSeverity.WARNING,
                        "STOCK_AGEING_UNMATCHED_PRODUCT", "Stock-ageing row is not matched to an existing product: " + nullSafe(row.productName),
                        "Map the product or keep the row as comparison-only data.", List.of(choice("IGNORE_WARNING", "Keep as comparison data", null)),
                        Map.of("resolutionKey", key, "entityType", "STOCK_AGEING", "stagingId", row.id.toString())));
                }
            }
        }
        return generated;
    }

    private void applySessionReviewState(ImportSession session) {
        var allIssues = issues.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(session.tenantId, session.id);
        var needsReview = allIssues.stream().anyMatch(issue -> !issue.resolved
            && (issue.severity == DomainEnums.ImportPlanIssueSeverity.ERROR || issue.severity == DomainEnums.ImportPlanIssueSeverity.REVIEW_REQUIRED));
        session.status = needsReview ? DomainEnums.ImportSessionStatus.NEEDS_REVIEW : DomainEnums.ImportSessionStatus.STAGED;
        sessions.save(session);
        plans.findByTenantIdAndImportSessionId(session.tenantId, session.id).ifPresent(plan -> {
            plan.status = needsReview ? DomainEnums.ImportPlanStatus.NEEDS_REVIEW : DomainEnums.ImportPlanStatus.READY;
            plan.planJson.put("phase", "CANONICAL_STAGING");
            plan.planJson.put("commitEnabled", false);
            plan.planJson.put("stagingComplete", true);
            plans.save(plan);
        });
    }

    private Map<String, Object> summary(ImportSession session) {
        var products = stagedProducts.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var parties = stagedParties.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var warehouses = stagedWarehouses.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var units = stagedUnits.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var snapshots = stagedSnapshots.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var vouchers = stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(session.tenantId, session.id);
        var items = stagedVoucherItems.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var cashbook = stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(session.tenantId, session.id);
        var ageing = stagedAgeing.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        return Map.ofEntries(
            Map.entry("productsStaged", products.size()), Map.entry("partiesStaged", parties.size()),
            Map.entry("warehousesStaged", warehouses.size()), Map.entry("unitsStaged", units.size()),
            Map.entry("stockSnapshotsStaged", snapshots.size()), Map.entry("vouchersStaged", vouchers.size()),
            Map.entry("voucherItemsStaged", items.size()),
            Map.entry("creditNotesStaged", vouchers.stream().filter(row -> normalize(row.voucherType).contains("CREDIT")).count()),
            Map.entry("debitNotesStaged", vouchers.stream().filter(row -> normalize(row.voucherType).contains("DEBIT")).count()),
            Map.entry("ledgerAdjustmentLinesStaged", vouchers.stream().mapToInt(row -> row.taxLineCount + row.discountLineCount
                + row.freightLineCount + row.roundOffLineCount + row.otherChargeLineCount).sum()),
            Map.entry("debtorRowsStaged", parties.stream().filter(row -> row.sourceType == DomainEnums.DetectedFileType.DEBTOR_CREDITOR_ANALYSIS
                && row.partyType == DomainEnums.SmartPartyType.CUSTOMER).count()),
            Map.entry("creditorRowsStaged", parties.stream().filter(row -> row.sourceType == DomainEnums.DetectedFileType.DEBTOR_CREDITOR_ANALYSIS
                && row.partyType == DomainEnums.SmartPartyType.SUPPLIER).count()),
            Map.entry("cashbookEntriesStaged", cashbook.size()),
            Map.entry("stockAgeingRowsStaged", ageing.size())
        );
    }

    private Map<String, Object> matchingSummary(ImportSession session) {
        var products = stagedProducts.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var parties = stagedParties.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var warehouses = stagedWarehouses.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(session.tenantId, session.id);
        var vouchers = stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(session.tenantId, session.id);
        var cashbook = stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(session.tenantId, session.id);
        return Map.ofEntries(
            Map.entry("productsToCreate", count(products, DomainEnums.SmartMatchStatus.CREATE_NEW)),
            Map.entry("productsMatchedExisting", count(products, DomainEnums.SmartMatchStatus.MATCH_EXISTING)),
            Map.entry("possibleDuplicateProducts", products.stream().filter(row -> row.matchStatus == DomainEnums.SmartMatchStatus.POSSIBLE_DUPLICATE_REVIEW || row.matchStatus == DomainEnums.SmartMatchStatus.AMBIGUOUS_REVIEW).count()),
            Map.entry("partiesToCreate", count(parties, DomainEnums.SmartMatchStatus.CREATE_NEW)),
            Map.entry("partiesMatchedExisting", count(parties, DomainEnums.SmartMatchStatus.MATCH_EXISTING)),
            Map.entry("warehousesToCreateOrMap", warehouses.stream().filter(row -> row.matchStatus != DomainEnums.SmartMatchStatus.MATCH_EXISTING).count()),
            Map.entry("warehousesMatchedExisting", count(warehouses, DomainEnums.SmartMatchStatus.MATCH_EXISTING)),
            Map.entry("vouchersLikelyDuplicates", count(vouchers, DomainEnums.SmartMatchStatus.SKIP_DUPLICATE)),
            Map.entry("cashbookRowsMatched", cashbook.stream().filter(this::cashbookReady).count()),
            Map.entry("cashbookRowsUnmatched", cashbook.stream().filter(row -> row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.UNMATCHED_REVIEW).count()),
            Map.entry("cashbookRowsReviewRequired", cashbook.stream().filter(row -> row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.LOW_CONFIDENCE_REVIEW).count()),
            Map.entry("paymentsToCreate", cashbook.stream().filter(this::cashbookReady).count())
        );
    }

    private long count(Collection<?> rows, DomainEnums.SmartMatchStatus status) {
        return rows.stream().filter(row -> matchStatus(row) == status).count();
    }

    private DomainEnums.SmartMatchStatus matchStatus(Object row) {
        if (row instanceof SmartStagedProduct value) return value.matchStatus;
        if (row instanceof SmartStagedParty value) return value.matchStatus;
        if (row instanceof SmartStagedWarehouse value) return value.matchStatus;
        if (row instanceof SmartStagedVoucher value) return value.matchStatus;
        return DomainEnums.SmartMatchStatus.UNKNOWN;
    }

    private void clearStaging(UUID tenantId, UUID sessionId) {
        dryRunItems.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        dryRuns.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedVoucherItems.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedSnapshots.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedCashbook.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedAgeing.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedVouchers.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedProducts.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedParties.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedWarehouses.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
        stagedUnits.deleteByTenantIdAndImportSessionId(tenantId, sessionId);
    }

    private void validateResolutionTarget(UUID tenantId, String action, UUID targetId) {
        if ("MAP_PRODUCT".equals(action) && (targetId == null || products.findByTenantIdAndId(tenantId, targetId).isEmpty())) {
            throw ApiErrors.badRequest("Select a product from the current tenant");
        }
        if ("MAP_WAREHOUSE".equals(action) && (targetId == null || warehouses.findByTenantIdAndId(tenantId, targetId).isEmpty())) {
            throw ApiErrors.badRequest("Select a warehouse from the current tenant");
        }
    }

    private ImportPlanIssue reviewIssue(
        ImportSession session, UUID fileId, int rowNumber, DomainEnums.ImportPlanIssueSeverity severity,
        String code, String message, String suggestedAction, List<Map<String, Object>> choices, Map<String, Object> context
    ) {
        var issue = new ImportPlanIssue();
        issue.tenantId = session.tenantId;
        issue.importSessionId = session.id;
        issue.affectedFileId = fileId;
        issue.affectedRows = rowNumber <= 0 ? new ArrayList<>() : new ArrayList<>(List.of(rowNumber));
        issue.severity = severity;
        issue.code = code;
        issue.message = message;
        issue.suggestedAction = suggestedAction;
        issue.availableChoices = new ArrayList<>(choices);
        issue.contextJson = new LinkedHashMap<>(context);
        return issue;
    }

    private Map<String, Object> reviewItem(ImportPlanIssue issue, String fileName) {
        var response = new LinkedHashMap<String, Object>();
        response.put("id", issue.id);
        response.put("severity", issue.severity);
        response.put("code", issue.code);
        response.put("message", issue.message);
        response.put("affectedFileId", issue.affectedFileId == null ? "" : issue.affectedFileId);
        response.put("affectedFile", fileName == null ? "Session" : fileName);
        response.put("affectedRows", issue.affectedRows);
        response.put("suggestedAction", nullSafe(issue.suggestedAction));
        response.put("availableChoices", issue.availableChoices);
        response.put("context", issue.contextJson);
        response.put("resolved", issue.resolved);
        response.put("resolution", issue.resolutionJson);
        return response;
    }

    private List<Map<String, Object>> fileTypeChoices() {
        return Arrays.stream(DomainEnums.DetectedFileType.values())
            .filter(type -> type != DomainEnums.DetectedFileType.UNKNOWN)
            .map(type -> {
                Map<String, Object> choice = new LinkedHashMap<>();
                choice.put("action", "OVERRIDE_FILE_TYPE");
                choice.put("label", type.name().replace('_', ' '));
                choice.put("selectedFileType", type.name());
                return choice;
            }).toList();
    }

    private Map<String, Object> choice(String action, String label, UUID targetId) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("action", action);
        choice.put("label", label);
        if (targetId != null) choice.put("targetId", targetId.toString());
        return choice;
    }

    private boolean ignored(Map<String, SmartImportResolution> resolutions, String key) {
        var resolution = resolutions.get(key);
        return resolution != null && "IGNORE_WARNING".equals(resolution.action);
    }

    private List<Product> possibleProducts(UUID tenantId, List<Product> existingProducts, String normalizedName, String unitCode) {
        if (normalizedName == null || normalizedName.isBlank()) return List.of();
        return existingProducts.stream()
            .filter(product -> product.normalizedName != null)
            .filter(product -> normalizeUnitOfProduct(tenantId, product).equals(nullSafe(unitCode)) || product.normalizedName.equalsIgnoreCase(normalizedName) || similarity(product.normalizedName, normalizedName) >= 0.72)
            .filter(product -> product.normalizedName.equalsIgnoreCase(normalizedName) || similarity(product.normalizedName, normalizedName) >= 0.72)
            .toList();
    }

    private String normalizeUnitOfProduct(UUID tenantId, Product product) {
        if (product.baseUnitId == null) return "";
        return units.findByTenantIdAndId(tenantId, product.baseUnitId).map(unit -> nullSafe(normalizeUnit(unit.code))).orElse("");
    }

    private Product findByExternalId(List<Product> existingProducts, String externalId) {
        if (externalId == null) return null;
        for (var product : existingProducts) {
            if (product.rawMetadata == null) continue;
            var candidate = raw(product.rawMetadata, "Tally GUID", "GUID", "MASTERID", "Master ID", "Tally Master ID");
            if (candidate != null && externalId.equalsIgnoreCase(candidate.trim())) return product;
        }
        return null;
    }

    private void productMatched(SmartStagedProduct row, Product product, DomainEnums.SmartReviewStatus reviewStatus) {
        row.matchedProductId = product.id;
        row.matchStatus = DomainEnums.SmartMatchStatus.MATCH_EXISTING;
        row.reviewStatus = reviewStatus;
    }

    private void warehouseMatched(SmartStagedWarehouse row, UUID warehouseId, DomainEnums.SmartReviewStatus reviewStatus) {
        row.matchedWarehouseId = warehouseId;
        row.matchStatus = DomainEnums.SmartMatchStatus.MATCH_EXISTING;
        row.reviewStatus = reviewStatus;
    }

    private ExistingVoucherStatus existingVoucherStatus(UUID tenantId, SmartStagedVoucher voucher) {
        if (voucher.voucherNumber == null) return ExistingVoucherStatus.NONE;
        var normalized = normalize(voucher.voucherType);
        if (normalized.contains("DEBIT")) {
            return financialAdjustments.findByTenantIdAndAdjustmentTypeAndVoucherNumberIgnoreCase(
                    tenantId, DomainEnums.FinancialAdjustmentType.DEBIT_NOTE, voucher.voucherNumber)
                .map(adjustment -> sameVoucher(adjustment.voucherDate, adjustment.supplierId, adjustment.totalAmount, voucher)
                    ? ExistingVoucherStatus.EXACT_DUPLICATE : ExistingVoucherStatus.AMBIGUOUS_CONFLICT)
                .orElse(ExistingVoucherStatus.NONE);
        }
        if (normalized.contains("CREDIT")) {
            return financialAdjustments.findByTenantIdAndAdjustmentTypeAndVoucherNumberIgnoreCase(
                    tenantId, DomainEnums.FinancialAdjustmentType.CREDIT_NOTE, voucher.voucherNumber)
                .map(adjustment -> sameVoucher(adjustment.voucherDate, adjustment.customerId, adjustment.totalAmount, voucher)
                    ? ExistingVoucherStatus.EXACT_DUPLICATE : ExistingVoucherStatus.AMBIGUOUS_CONFLICT)
                .orElse(ExistingVoucherStatus.NONE);
        }
        if (normalized.contains("PURCHASE")) {
            return purchaseInvoices.findByTenantIdAndInvoiceNumberIgnoreCase(tenantId, voucher.voucherNumber)
                .map(invoice -> sameVoucher(invoice.invoiceDate, invoice.supplierId, invoice.totalAmount, voucher)
                    ? ExistingVoucherStatus.EXACT_DUPLICATE : ExistingVoucherStatus.AMBIGUOUS_CONFLICT)
                .orElse(ExistingVoucherStatus.NONE);
        }
        if (normalized.contains("SALES") || normalized.contains("SALE")) {
            return salesInvoices.findByTenantIdAndInvoiceNumberIgnoreCase(tenantId, voucher.voucherNumber)
                .map(invoice -> sameVoucher(invoice.invoiceDate, invoice.customerId, invoice.totalAmount, voucher)
                    ? ExistingVoucherStatus.EXACT_DUPLICATE : ExistingVoucherStatus.AMBIGUOUS_CONFLICT)
                .orElse(ExistingVoucherStatus.NONE);
        }
        return ExistingVoucherStatus.NONE;
    }

    private boolean sameVoucher(LocalDate date, UUID partyId, BigDecimal total, SmartStagedVoucher voucher) {
        return Objects.equals(date, voucher.voucherDate)
            && partyId != null && voucher.matchedPartyId != null
            && Objects.equals(partyId, voucher.matchedPartyId)
            && nvl(total).compareTo(nvl(voucher.totalAmount)) == 0;
    }

    private enum ExistingVoucherStatus { NONE, EXACT_DUPLICATE, AMBIGUOUS_CONFLICT }

    private record CashbookPartyMatch(DomainEnums.SmartPartyType type, UUID id, String normalizedName) { }

    private record CashbookInvoiceMatch(
        DomainEnums.SmartPartyType partyType,
        UUID partyId,
        UUID invoiceId,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal amount
    ) { }

    private String fingerprint(UUID tenantId, SmartStagedVoucher voucher) {
        var raw = tenantId + "|" + normalize(voucher.voucherType) + "|" + normalize(voucher.voucherNumber) + "|"
            + voucher.voucherDate + "|" + nullSafe(voucher.normalizedPartyName) + "|" + nvl(voucher.totalAmount).stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String cashbookFingerprint(UUID tenantId, SmartStagedCashbookEntry entry) {
        var raw = tenantId + "|" + entry.entryDate + "|" + normalizeName(entry.partyName) + "|"
            + nvl(entry.amount).stripTrailingZeros().toPlainString() + "|" + entry.direction + "|"
            + normalize(entry.referenceNumber);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private DomainEnums.PaymentMode paymentMode(String rawMode, String partyName) {
        var normalized = normalize(firstNonBlank(rawMode, partyName));
        if (normalized.contains("UPI")) return DomainEnums.PaymentMode.UPI;
        if (normalized.contains("CHEQUE") || normalized.contains("CHECK")) return DomainEnums.PaymentMode.CHEQUE;
        if (normalized.contains("BANK") || normalized.contains("NEFT") || normalized.contains("RTGS")
            || normalized.contains("IMPS")) return DomainEnums.PaymentMode.BANK;
        if (normalized.contains("CASH")) return DomainEnums.PaymentMode.CASH;
        return DomainEnums.PaymentMode.OTHER;
    }

    private DomainEnums.SmartPartyType partyType(DomainEnums.DetectedFileType sourceType, Map<String, String> row) {
        if (sourceType == DomainEnums.DetectedFileType.SALES_VOUCHERS || sourceType == DomainEnums.DetectedFileType.CREDIT_NOTES) {
            return DomainEnums.SmartPartyType.CUSTOMER;
        }
        if (sourceType == DomainEnums.DetectedFileType.PURCHASE_VOUCHERS || sourceType == DomainEnums.DetectedFileType.DEBIT_NOTES) {
            return DomainEnums.SmartPartyType.SUPPLIER;
        }
        var group = normalize(lookup(row, "Party Type", "Ledger Group", "Parent", "Group"));
        if (group.contains("CUSTOMER") || group.contains("DEBTOR") || group.contains("RECEIVABLE")) return DomainEnums.SmartPartyType.CUSTOMER;
        if (group.contains("SUPPLIER") || group.contains("CREDITOR") || group.contains("PAYABLE")) return DomainEnums.SmartPartyType.SUPPLIER;
        return DomainEnums.SmartPartyType.UNKNOWN;
    }

    private String defaultVoucherType(DomainEnums.DetectedFileType type) {
        return switch (type) {
            case SALES_VOUCHERS -> "Sales";
            case PURCHASE_VOUCHERS -> "Purchase";
            case CREDIT_NOTES -> "Credit Note";
            case DEBIT_NOTES -> "Debit Note";
            case STOCK_JOURNAL -> "Stock Journal";
            default -> type.name();
        };
    }

    private ImportSession session(UUID sessionId) {
        return sessions.findByTenantIdAndId(TenantContext.tenantId(), sessionId)
            .orElseThrow(() -> ApiErrors.notFound("Import session not found"));
    }

    private void ensureMutable(ImportSession session) {
        if (session.status == DomainEnums.ImportSessionStatus.CANCELLED) throw ApiErrors.conflict("Cancelled import sessions cannot be changed");
        if (session.status == DomainEnums.ImportSessionStatus.COMMITTING || session.status == DomainEnums.ImportSessionStatus.COMMITTED) {
            throw ApiErrors.conflict("This import session can no longer be changed");
        }
    }

    private void own(TenantOwnedEntity entity, ImportSession session, ImportSessionFile file, int rowNumber) {
        entity.tenantId = session.tenantId;
        entity.createdBy = TenantContext.userId();
        entity.updatedBy = TenantContext.userId();
        if (entity instanceof SmartStagedProduct row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedParty row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedWarehouse row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedUnit row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedStockSnapshot row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedVoucher row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedVoucherItem row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedCashbookEntry row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
        else if (entity instanceof SmartStagedStockAgeing row) { row.importSessionId = session.id; row.sessionFileId = file.id; row.sourceRowNumber = rowNumber; }
    }

    private Map<String, Object> productRow(SmartStagedProduct row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "row", row.sourceRowNumber, "name", row.rawName,
            "normalizedName", row.normalizedName, "unitCode", row.unitCode, "sku", row.sku, "category", row.categoryName,
            "matchedProductId", row.matchedProductId, "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus);
    }

    private Map<String, Object> partyRow(SmartStagedParty row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "row", row.sourceRowNumber, "name", row.rawName,
            "partyType", row.partyType, "sourceType", row.sourceType, "gstin", row.gstin,
            "outstandingAmount", row.openingBalance, "snapshotDate", row.snapshotDate,
            "matchedCustomerId", row.matchedCustomerId,
            "matchedSupplierId", row.matchedSupplierId, "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus);
    }

    private Map<String, Object> warehouseRow(SmartStagedWarehouse row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "name", row.sourceWarehouseName,
            "matchedWarehouseId", row.matchedWarehouseId, "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus);
    }

    private Map<String, Object> unitRow(SmartStagedUnit row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "code", row.sourceUnitCode,
            "matchedUnitId", row.matchedUnitId, "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus);
    }

    private Map<String, Object> snapshotRow(SmartStagedStockSnapshot row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "row", row.sourceRowNumber, "productName", row.productName,
            "unitCode", row.unitCode, "warehouseName", row.warehouseName, "snapshotDate", row.snapshotDate,
            "currentStock", row.currentStock, "importedStock", row.importedStock, "deltaPreview", row.deltaPreview,
            "matchedProductId", row.matchedProductId, "matchedWarehouseId", row.matchedWarehouseId,
            "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus, "action", row.action);
    }

    private Map<String, Object> voucherRow(SmartStagedVoucher row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "row", row.sourceRowNumber, "voucherType", row.voucherType,
            "voucherNumber", row.voucherNumber, "voucherDate", row.voucherDate, "partyName", row.partyName,
            "totalAmount", row.totalAmount, "matchedPartyId", row.matchedPartyId, "matchStatus", row.matchStatus,
            "reviewStatus", row.reviewStatus, "stockImpactModeSuggestion", row.stockImpactModeSuggestion,
            "taxAmount", row.taxAmount, "discountAmount", row.discountAmount, "freightAmount", row.freightAmount,
            "roundOffAmount", row.roundOffAmount, "otherChargesAmount", row.otherChargesAmount,
            "taxLineCount", row.taxLineCount, "discountLineCount", row.discountLineCount,
            "freightLineCount", row.freightLineCount, "roundOffLineCount", row.roundOffLineCount,
            "otherChargeLineCount", row.otherChargeLineCount);
    }

    private Map<String, Object> voucherItemRow(SmartStagedVoucherItem row) {
        return linked("id", row.id, "voucherId", row.stagedVoucherId, "fileId", row.sessionFileId, "row", row.sourceRowNumber,
            "productName", row.productName, "unitCode", row.unitCode, "quantity", row.quantity, "rate", row.rate,
            "amount", row.amount, "warehouseName", row.warehouseName, "rateSource", row.rateSource,
            "matchedProductId", row.matchedProductId, "matchedWarehouseId", row.matchedWarehouseId,
            "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus);
    }

    private Map<String, Object> cashbookRow(SmartStagedCashbookEntry row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "row", row.sourceRowNumber, "date", row.entryDate,
            "partyName", row.partyName, "amount", row.amount, "direction", row.direction,
            "matchedPartyId", row.matchedPartyId, "matchedInvoiceId", row.matchedInvoiceId,
            "matchedPartyType", row.matchedPartyType, "cashbookMatchStatus", row.cashbookMatchStatus,
            "paymentMode", row.paymentMode, "referenceNumber", row.referenceNumber,
            "matchConfidence", row.matchConfidence, "matchReason", row.matchReason,
            "resolutionStatus", row.resolutionStatus, "manualResolutionAction", row.manualResolutionAction,
            "customerPaymentId", row.customerPaymentId, "supplierPaymentId", row.supplierPaymentId,
            "resolvedAt", row.resolvedAt, "resolutionNote", row.resolutionNote,
            "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus);
    }

    private boolean cashbookReady(SmartStagedCashbookEntry row) {
        return row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_CUSTOMER_PAYMENT
            || row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_SUPPLIER_PAYMENT
            || row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_SALES_INVOICE
            || row.cashbookMatchStatus == DomainEnums.CashbookMatchStatus.MATCHED_PURCHASE_INVOICE;
    }

    private Map<String, Object> ageingRow(SmartStagedStockAgeing row) {
        return linked("id", row.id, "fileId", row.sessionFileId, "row", row.sourceRowNumber, "productName", row.productName,
            "unitCode", row.unitCode, "quantity", row.quantity, "ageingBucket", row.ageingBucket, "daysOld", row.daysOld,
            "value", row.stockValue, "matchedProductId", row.matchedProductId, "matchStatus", row.matchStatus, "reviewStatus", row.reviewStatus);
    }

    private Map<String, Object> linked(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1] == null ? "" : values[index + 1]);
        }
        return result;
    }

    private String lookup(Map<String, String> row, String... aliases) {
        for (var alias : aliases) {
            var expected = normalizeKey(alias);
            for (var entry : row.entrySet()) {
                if (normalizeKey(entry.getKey()).equals(expected) && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue().trim();
                }
            }
        }
        return null;
    }

    private String raw(Map<String, Object> row, String... aliases) {
        for (var alias : aliases) {
            var expected = normalizeKey(alias);
            for (var entry : row.entrySet()) {
                if (normalizeKey(entry.getKey()).equals(expected) && entry.getValue() != null) return String.valueOf(entry.getValue());
            }
        }
        return null;
    }

    private Map<String, Object> metadata(Map<String, String> row) {
        var result = new LinkedHashMap<String, Object>();
        result.putAll(row);
        return result;
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            var cleaned = value.replace(",", "").replaceAll("[^0-9.\\-]", "");
            return cleaned.isBlank() || "-".equals(cleaned) ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        for (var format : DATE_FORMATS) {
            try { return LocalDate.parse(value.trim(), format); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private Integer integer(String value) {
        var number = decimal(value);
        return number == null ? null : number.setScale(0, RoundingMode.DOWN).intValue();
    }

    private BigDecimal abs(BigDecimal value) {
        return value == null ? null : value.abs();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String normalizeName(String value) {
        return CatalogService.normalizeName(value);
    }

    private String normalizeUnit(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizeKey(String value) {
        return normalize(value).replaceAll("[^A-Z0-9]", "");
    }

    private String productKey(String normalizedName, String unitCode) {
        return nullSafe(normalizedName) + "|" + nullSafe(unitCode);
    }

    private String productResolutionKey(String normalizedName, String unitCode) {
        return "PRODUCT:" + productKey(normalizedName, unitCode);
    }

    private String warehouseResolutionKey(String normalizedName) {
        return "WAREHOUSE:" + nullSafe(normalizedName);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (var value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private UUID firstNonNull(UUID first, UUID second) {
        return first == null ? second : first;
    }

    private String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double similarity(String left, String right) {
        var a = normalizeKey(left);
        var b = normalizeKey(right);
        if (a.equals(b)) return 1.0;
        if (a.isBlank() || b.isBlank()) return 0;
        var previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            var current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                var cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return 1.0 - ((double) previous[b.length()] / Math.max(a.length(), b.length()));
    }

    private DomainEnums.DetectedFileType effectiveType(ImportSessionFile file) {
        return file.selectedFileType == null ? file.detectedFileType : file.selectedFileType;
    }

    private String safeMessage(Exception ex) {
        var message = ex.getMessage();
        return message == null || message.isBlank() ? "invalid file content" : message.replaceAll("[\\r\\n]+", " ");
    }

    private record ParseFailure(ImportSessionFile file, String message) { }
}
