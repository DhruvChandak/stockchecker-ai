package com.stockpilot.ai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.config.JwtService;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.domain.UserAccount;
import com.stockpilot.ai.domain.UserTenantMembership;
import com.stockpilot.ai.domain.SalesInvoice;
import com.stockpilot.ai.domain.PurchaseInvoice;
import com.stockpilot.ai.domain.StockMovement;
import com.stockpilot.ai.domain.ExternalRecordMapping;
import com.stockpilot.ai.repo.Repositories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartImportSessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Repositories.UserRepository users;
    @Autowired Repositories.MembershipRepository memberships;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired Repositories.StockMovementRepository stockMovements;
    @Autowired Repositories.ProductRepository products;
    @Autowired Repositories.SalesInvoiceRepository salesInvoices;
    @Autowired Repositories.SalesInvoiceItemRepository salesInvoiceItems;
    @Autowired Repositories.PurchaseInvoiceRepository purchaseInvoices;
    @Autowired Repositories.PurchaseInvoiceItemRepository purchaseInvoiceItems;
    @Autowired Repositories.FinancialAdjustmentRepository financialAdjustments;
    @Autowired Repositories.FinancialAdjustmentItemRepository financialAdjustmentItems;
    @Autowired Repositories.SmartStagedVoucherRepository stagedVouchers;
    @Autowired Repositories.SmartStagedVoucherItemRepository stagedVoucherItems;
    @Autowired Repositories.ImportDryRunRepository dryRuns;
    @Autowired Repositories.ImportDryRunItemRepository dryRunItems;
    @Autowired Repositories.ImportSessionFileRepository importSessionFiles;
    @Autowired Repositories.SmartImportCommitRepository smartCommits;
    @Autowired Repositories.SmartImportEffectRepository smartEffects;
    @Autowired Repositories.ExternalRecordMappingRepository externalMappings;
    @Autowired Repositories.AuditLogRepository auditLogs;
    @Autowired Repositories.TenantRepository tenants;
    @Autowired Repositories.WarehouseRepository warehouses;
    @Autowired Repositories.CustomerPaymentRepository customerPayments;
    @Autowired Repositories.SupplierPaymentRepository supplierPayments;
    @Autowired Repositories.OutstandingSnapshotRepository outstandingSnapshots;
    @Autowired Repositories.SmartStagedCashbookEntryRepository stagedCashbook;
    @Autowired Repositories.CustomerRepository customers;
    @Autowired Repositories.SupplierRepository suppliers;

    @Test
    void createsAndListsImportSession() throws Exception {
        var registration = register("smart-create");
        var token = registration.get("accessToken").asText();

        var session = createSession(token, "Quarter-end Tally files");
        assertThat(session.get("name").asText()).isEqualTo("Quarter-end Tally files");
        assertThat(session.get("status").asText()).isEqualTo("CREATED");

        var list = getJson(token, "/api/import-sessions?size=20");
        assertThat(list.get("content").findValuesAsText("id")).contains(session.get("id").asText());
    }

    @Test
    void uploadsMultipleFilesAndClassifiesThemWithoutCreatingBusinessData() throws Exception {
        var token = register("smart-multi").get("accessToken").asText();
        var sessionId = createSession(token, "Mixed exports").get("id").asText();

        upload(token, sessionId, fixture("sales.xml"), fixture("purchase.xml"));
        var classified = postJson(token, "/api/import-sessions/" + sessionId + "/classify");

        assertThat(classified.get("files")).hasSize(2);
        assertThat(classified.get("files").findValuesAsText("detectedFileType"))
            .containsExactlyInAnyOrder("SALES_VOUCHERS", "PURCHASE_VOUCHERS");
    }

    @Test
    void warnsForDuplicateFileHashAndReusesClassification() throws Exception {
        var token = register("smart-duplicate").get("accessToken").asText();
        var sessionId = createSession(token, "Duplicate check").get("id").asText();

        var uploaded = upload(token, sessionId, fixture("closing_stock_full_company.xml"), fixture("duplicate_file.xml"));
        assertThat(uploaded.get("issues").findValuesAsText("code")).contains("DUPLICATE_FILE");
        var classified = postJson(token, "/api/import-sessions/" + sessionId + "/classify");

        assertThat(classified.get("files").findValuesAsText("status")).contains("DUPLICATE");
        assertThat(classified.get("files").findValuesAsText("detectedFileType"))
            .containsOnly("STOCK_SNAPSHOT");
        assertThat(classified.get("issues").findValuesAsText("code")).contains("DUPLICATE_FILE");
    }

    @Test
    void buildsSnapshotFirstPlanForSnapshotOnly() throws Exception {
        var token = register("smart-snapshot").get("accessToken").asText();
        var sessionId = createSession(token, "Snapshot").get("id").asText();
        upload(token, sessionId, fixture("closing_stock_full_company.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");

        var plan = postJson(token, "/api/import-sessions/" + sessionId + "/plan");
        assertThat(plan.get("strategy").asText()).isEqualTo("SNAPSHOT_FIRST");
        assertThat(plan.get("details").get("commitEnabled").asBoolean()).isFalse();
    }

    @Test
    void buildsTransactionHistoryPlanWithoutSnapshot() throws Exception {
        var token = register("smart-history").get("accessToken").asText();
        var sessionId = createSession(token, "Transaction history").get("id").asText();
        upload(token, sessionId, fixture("sales.xml"), fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");

        var plan = postJson(token, "/api/import-sessions/" + sessionId + "/plan");
        assertThat(plan.get("strategy").asText()).isEqualTo("TRANSACTION_HISTORY");
    }

    @Test
    void buildsHybridPlanAndWarningWhenSnapshotAndTransactionsExist() throws Exception {
        var token = register("smart-hybrid").get("accessToken").asText();
        var sessionId = createSession(token, "Hybrid set").get("id").asText();
        upload(token, sessionId, fixture("closing_stock_full_company.xml"), fixture("sales.xml"), fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");

        var plan = postJson(token, "/api/import-sessions/" + sessionId + "/plan");
        assertThat(plan.get("strategy").asText()).isEqualTo("HYBRID_RECONCILIATION");
        assertThat(plan.get("issues").findValuesAsText("code")).contains("TRANSACTION_FILES_WITH_SNAPSHOT");
    }

    @Test
    void unknownFileMovesSessionToNeedsReview() throws Exception {
        var token = register("smart-unknown").get("accessToken").asText();
        var sessionId = createSession(token, "Unknown input").get("id").asText();
        upload(token, sessionId, fixture("unknown_file.xml"));

        var classified = postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        assertThat(classified.get("status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(classified.get("issues").findValuesAsText("code"))
            .contains("UNKNOWN_FILE_TYPE", "REVIEW_REQUIRED_FOR_UNCERTAIN_FILES");
    }

    @Test
    void tenantCannotAccessAnotherTenantSession() throws Exception {
        var tokenA = register("smart-tenant-a").get("accessToken").asText();
        var sessionId = createSession(tokenA, "Tenant A private files").get("id").asText();
        var tokenB = register("smart-tenant-b").get("accessToken").asText();

        mvc.perform(get("/api/import-sessions/{id}", sessionId).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound());
    }

    @Test
    void customerUserCannotAccessSmartImportSessions() throws Exception {
        var registration = register("smart-customer-owner");
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var customerToken = tokenForRole(tenantId, DomainEnums.Role.CUSTOMER_USER);

        mvc.perform(get("/api/import-sessions").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/import-sessions")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forbidden\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void stagesSnapshotAndCalculatesDeltaWithoutCreatingMovement() throws Exception {
        var registration = register("smart-stage-snapshot");
        var token = registration.get("accessToken").asText();
        var product = postJson(token, "/api/products", """
            {"sku":"SAMPLE-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Snapshot Warehouse\",\"code\":\"SNAP\"}");
        postJson(token, "/api/stock/adjustment", """
            {"productId":"%s","warehouseId":"%s","quantityDelta":40,"rate":10,"notes":"Snapshot preview test"}
            """.formatted(product.get("id").asText(), warehouse.get("id").asText()));
        var movementCount = stockMovements.count();

        var workspace = uploadClassifyAndStage(token, "Snapshot stage", fixture("closing_stock_full_company.xml"));
        assertThat(workspace.get("summary").get("stockSnapshotsStaged").asInt()).isEqualTo(1);
        assertThat(workspace.get("snapshots").get(0).get("currentStock").decimalValue()).isEqualByComparingTo("40");
        assertThat(workspace.get("snapshots").get(0).get("deltaPreview").decimalValue()).isEqualByComparingTo("60");
        assertThat(stockMovements.count()).isEqualTo(movementCount);
    }

    @Test
    void stagesSalesPurchaseVouchersAndSuggestsInvoiceOnlyWithSnapshot() throws Exception {
        var token = register("smart-stage-vouchers").get("accessToken").asText();
        var workspace = uploadClassifyAndStage(token, "Voucher stage",
            fixture("closing_stock_full_company.xml"), fixture("sales.xml"), fixture("purchase.xml"));

        assertThat(workspace.get("summary").get("vouchersStaged").asInt()).isEqualTo(2);
        assertThat(workspace.get("summary").get("voucherItemsStaged").asInt()).isEqualTo(2);
        assertThat(workspace.get("vouchers").findValuesAsText("stockImpactModeSuggestion"))
            .containsOnly("CREATE_INVOICES_ONLY");
    }

    @Test
    void stagesDebtorCreditorPartiesAndMatchesCustomerByGstin() throws Exception {
        var token = register("smart-stage-parties").get("accessToken").asText();
        postJson(token, "/api/customers", """
            {"name":"Existing Customer Alias","gstin":"27ABCDE1234F1Z5","creditLimit":10000}
            """);
        var workspace = uploadClassifyAndStage(token, "Party stage", fixture("debtors_creditors.xml"));

        assertThat(workspace.get("summary").get("partiesStaged").asInt()).isEqualTo(2);
        assertThat(workspace.get("parties").findValuesAsText("matchStatus")).contains("MATCH_EXISTING");
    }

    @Test
    void stagesCashbookAndStockAgeingRows() throws Exception {
        var token = register("smart-stage-comparison").get("accessToken").asText();
        var workspace = uploadClassifyAndStage(token, "Comparison stage", fixture("cashbook.xml"), fixture("stock_ageing.csv"));

        assertThat(workspace.get("summary").get("cashbookEntriesStaged").asInt()).isEqualTo(2);
        assertThat(workspace.get("summary").get("stockAgeingRowsStaged").asInt()).isEqualTo(2);
        assertThat(workspace.get("reviewItems").findValuesAsText("code"))
            .contains("CASHBOOK_UNMATCHED_ENTRY", "STOCK_AGEING_UNMATCHED_PRODUCT");
    }

    @Test
    void exactProductNameAndUnitMatchExistingProduct() throws Exception {
        var token = register("smart-product-match").get("accessToken").asText();
        postJson(token, "/api/products", """
            {"sku":"EXACT-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var workspace = uploadClassifyAndStage(token, "Exact product", fixture("inventory_master.xml"));

        assertThat(workspace.get("products").findValuesAsText("name")).contains("Sample Item A");
        assertThat(workspace.get("products").findValuesAsText("matchStatus")).contains("MATCH_EXISTING");
    }

    @Test
    void similarProductIsMarkedForDuplicateReview() throws Exception {
        var token = register("smart-product-duplicate").get("accessToken").asText();
        postJson(token, "/api/products", """
            {"sku":"MAGGI-EXISTING","name":"MAGGI MASALA 70","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var workspace = uploadClassifyAndStage(token, "Duplicate-like product", fixture("duplicate_products.csv"));

        assertThat(workspace.get("products").findValuesAsText("matchStatus")).contains("POSSIBLE_DUPLICATE_REVIEW");
        assertThat(workspace.get("reviewItems").findValuesAsText("code")).contains("POSSIBLE_DUPLICATE_PRODUCT");
    }

    @Test
    void warehouseMatchingFindsExactAndReviewsUnknownGodown() throws Exception {
        var token = register("smart-warehouse-match").get("accessToken").asText();
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"MAINLOC\"}");
        var workspace = uploadClassifyAndStage(token, "Warehouse matching", fixture("sales.xml"), fixture("purchase.xml"));

        assertThat(workspace.get("matching").get("warehousesMatchedExisting").asInt()).isEqualTo(1);
        assertThat(workspace.get("reviewItems").findValuesAsText("code")).contains("UNKNOWN_WAREHOUSE");
    }

    @Test
    void fileTypeOverrideClearsAndRestagesFile() throws Exception {
        var token = register("smart-type-override").get("accessToken").asText();
        var sessionId = createSession(token, "Override type").get("id").asText();
        var uploaded = upload(token, sessionId, fixture("stock_ageing.csv"));
        var fileId = uploaded.get("files").get(0).get("id").asText();
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var first = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(first.get("summary").get("stockAgeingRowsStaged").asInt()).isEqualTo(2);

        var overridden = mapper.readTree(mvc.perform(patch("/api/import-sessions/{sessionId}/files/{fileId}/type", sessionId, fileId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFileType\":\"INVENTORY_MASTER\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(overridden.get("summary").get("stockAgeingRowsStaged").asInt()).isZero();
        assertThat(overridden.get("summary").get("productsStaged").asInt()).isEqualTo(2);
    }

    @Test
    void removingSessionFileDeletesItAndClearsDerivedWorkspace() throws Exception {
        var token = register("smart-remove-file").get("accessToken").asText();
        var sessionId = createSession(token, "Remove stale file").get("id").asText();
        var uploaded = upload(token, sessionId, fixture("closing_stock_full_company.xml"), fixture("sales.xml"), fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var staged = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(staged.get("summary").get("vouchersStaged").asInt()).isEqualTo(2);

        String purchaseFileId = null;
        for (var file : uploaded.get("files")) {
            if ("purchase.xml".equals(file.get("originalFileName").asText())) {
                purchaseFileId = file.get("id").asText();
                break;
            }
        }
        assertThat(purchaseFileId).isNotNull();

        var afterDelete = mapper.readTree(mvc.perform(delete("/api/import-sessions/{sessionId}/files/{fileId}", sessionId, purchaseFileId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(afterDelete.get("summary").get("vouchersStaged").asInt()).isZero();

        var details = getJson(token, "/api/import-sessions/" + sessionId);
        assertThat(details.get("files").findValuesAsText("originalFileName")).doesNotContain("purchase.xml");
        assertThat(details.get("files")).hasSize(2);
        assertThat(details.get("status").asText()).isEqualTo("CLASSIFIED");

        var restaged = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(restaged.get("summary").get("vouchersStaged").asInt()).isEqualTo(1);
    }

    @Test
    void rebuildReplacesCanonicalStagingInsteadOfAppending() throws Exception {
        var token = register("smart-rebuild").get("accessToken").asText();
        var sessionId = createSession(token, "Rebuild").get("id").asText();
        upload(token, sessionId, fixture("inventory_master.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var first = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var second = postJson(token, "/api/import-sessions/" + sessionId + "/rebuild");
        assertThat(second.get("summary").get("productsStaged").asInt())
            .isEqualTo(first.get("summary").get("productsStaged").asInt());
    }

    @Test
    void warehouseReviewResolutionPersistsAcrossRebuild() throws Exception {
        var token = register("smart-review-resolution").get("accessToken").asText();
        var target = postJson(token, "/api/warehouses", "{\"name\":\"Mapped Purchase Godown\",\"code\":\"MAP-PUR\"}");
        var sessionId = createSession(token, "Review resolution").get("id").asText();
        upload(token, sessionId, fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var first = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        JsonNode reviewItem = null;
        for (var item : first.get("reviewItems")) {
            if ("UNKNOWN_WAREHOUSE".equals(item.get("code").asText())) {
                reviewItem = item;
                break;
            }
        }
        assertThat(reviewItem).isNotNull();

        var resolved = postJson(token,
            "/api/import-sessions/" + sessionId + "/review-items/" + reviewItem.get("id").asText() + "/resolve",
            "{\"action\":\"MAP_WAREHOUSE\",\"targetId\":\"" + target.get("id").asText() + "\"}");

        assertThat(resolved.get("matching").get("warehousesMatchedExisting").asInt()).isEqualTo(1);
        assertThat(resolved.get("reviewItems").findValuesAsText("code")).doesNotContain("UNKNOWN_WAREHOUSE");
        var rebuilt = postJson(token, "/api/import-sessions/" + sessionId + "/rebuild");
        assertThat(rebuilt.get("matching").get("warehousesMatchedExisting").asInt()).isEqualTo(1);
    }

    @Test
    void invalidXmlControlCharactersAreSanitizedForClassificationAndStaging() throws Exception {
        var token = register("smart-invalid-xml").get("accessToken").asText();
        var sessionId = createSession(token, "Invalid XML").get("id").asText();
        upload(token, sessionId, fixture("invalid_control_chars.xml"));
        var classified = postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        assertThat(classified.get("files").get(0).get("detectedFileType").asText()).isEqualTo("INVENTORY_MASTER");

        var staged = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(staged.get("summary").get("productsStaged").asInt()).isEqualTo(1);
        assertThat(staged.get("products").get(0).get("name").asText()).isEqualTo("Control Character Item");
    }

    @Test
    void stagingAndReviewRemainTenantScopedAndPortalDenied() throws Exception {
        var registrationA = register("smart-stage-tenant-a");
        var tokenA = registrationA.get("accessToken").asText();
        var sessionId = createSession(tokenA, "Private staging").get("id").asText();
        upload(tokenA, sessionId, fixture("sales.xml"));
        postJson(tokenA, "/api/import-sessions/" + sessionId + "/classify");
        postJson(tokenA, "/api/import-sessions/" + sessionId + "/stage");

        var tokenB = register("smart-stage-tenant-b").get("accessToken").asText();
        mvc.perform(get("/api/import-sessions/{id}/staging", sessionId).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound());

        var customerToken = tokenForRole(UUID.fromString(registrationA.get("tenantId").asText()), DomainEnums.Role.CUSTOMER_USER);
        mvc.perform(get("/api/import-sessions/{id}/review-items", sessionId).header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/import-sessions/{id}/stage", sessionId).header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void dryRunSimulatesStagedSessionWithoutWritingFinalBusinessData() throws Exception {
        var registration = register("smart-dry-run-safe");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var sessionId = createSession(token, "Dry run safety").get("id").asText();
        upload(token, sessionId, fixture("sales.xml"), fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var productCount = products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).size();
        var salesCount = salesInvoices.findByTenantId(tenantId).size();
        var purchaseCount = purchaseInvoices.findByTenantId(tenantId).size();
        var movementCount = stockMovements.count();

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");

        assertThat(dryRun.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(dryRun.get("summary").get("dryRunOnly").asBoolean()).isTrue();
        assertThat(dryRun.get("summary").get("finalBusinessWrites").asInt()).isZero();
        assertThat(products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)).hasSize(productCount);
        assertThat(salesInvoices.findByTenantId(tenantId)).hasSize(salesCount);
        assertThat(purchaseInvoices.findByTenantId(tenantId)).hasSize(purchaseCount);
        assertThat(stockMovements.count()).isEqualTo(movementCount);
    }

    @Test
    void snapshotDryRunUsesDateAwareDeltaAndNoChangeForSameQuantity() throws Exception {
        var registration = register("smart-dry-run-snapshot-same");
        var token = registration.get("accessToken").asText();
        var product = postJson(token, "/api/products", """
            {"sku":"SNAP-100","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"MAIN-100\"}");
        saveMovementAt(UUID.fromString(registration.get("tenantId").asText()), product, warehouse, "100", java.time.Instant.parse("2026-06-29T12:00:00Z"));
        var movementCount = stockMovements.count();
        var sessionId = createSession(token, "Same snapshot").get("id").asText();
        upload(token, sessionId, fixture("closing_stock_full_company.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{}");
        var itemPage = getJson(token, "/api/import-sessions/" + sessionId + "/dry-run/items?itemType=STOCK_SNAPSHOT");

        assertThat(dryRun.get("summary").get("snapshotNoChangeRows").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("snapshotAdjustmentsPreviewed").asInt()).isZero();
        assertThat(itemPage.get("content").get(0).get("action").asText()).isEqualTo("NO_CHANGE");
        assertThat(itemPage.get("content").get(0).get("preview").get("deltaAtSnapshotDate").decimalValue()).isEqualByComparingTo("0");
        assertThat(stockMovements.count()).isEqualTo(movementCount);
    }

    @Test
    void changedSnapshotDryRunPreviewsOnlyRequiredDelta() throws Exception {
        var registration = register("smart-dry-run-snapshot-change");
        var token = registration.get("accessToken").asText();
        var product = postJson(token, "/api/products", """
            {"sku":"SNAP-120","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"MAIN-120\"}");
        saveMovementAt(UUID.fromString(registration.get("tenantId").asText()), product, warehouse, "100", java.time.Instant.parse("2026-06-29T12:00:00Z"));
        var sessionId = createSession(token, "Changed snapshot").get("id").asText();
        upload(token, sessionId, fixture("changed_closing_stock_snapshot_120.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{}");
        var itemPage = getJson(token, "/api/import-sessions/" + sessionId + "/dry-run/items?itemType=STOCK_SNAPSHOT");

        assertThat(dryRun.get("summary").get("snapshotAdjustmentsPreviewed").asInt()).isEqualTo(1);
        assertThat(itemPage.get("content").get(0).get("preview").get("deltaAtSnapshotDate").decimalValue()).isEqualByComparingTo("20");
        assertThat(itemPage.get("content").get(0).get("preview").get("projectedCurrentStockAfterApplyingLaterTransactions").decimalValue()).isEqualByComparingTo("120");
    }

    @Test
    void hybridDryRunSkipsBeforeSnapshotAndAppliesAfterSnapshotVoucherStock() throws Exception {
        var token = register("smart-dry-run-hybrid").get("accessToken").asText();
        var product = postJson(token, "/api/products", """
            {"sku":"HYB-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"HYB-MAIN\"}");
        postJson(token, "/api/stock/adjustment", """
            {"productId":"%s","warehouseId":"%s","quantityDelta":100,"rate":10,"notes":"Hybrid dry-run stock"}
            """.formatted(product.get("id").asText(), warehouse.get("id").asText()));
        var sessionId = createSession(token, "Hybrid dry run").get("id").asText();
        upload(token, sessionId, fixture("closing_stock_full_company.xml"), fixture("sales.xml"), fixture("sales_after_snapshot.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", """
            {"strategy":"HYBRID_RECONCILIATION","stockImpactMode":"APPLY_STOCK_MOVEMENTS_AFTER_SNAPSHOT_DATE"}
            """);

        assertThat(dryRun.get("summary").get("salesInvoicesPreviewed").asInt()).isEqualTo(2);
        assertThat(dryRun.get("summary").get("stockMovementsSkippedBeforeSnapshotDate").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(dryRun.get("summary").get("stockMovementsPreviewed").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void transactionHistoryDryRunPreviewsPurchaseAndSaleMovements() throws Exception {
        var token = register("smart-dry-run-history").get("accessToken").asText();
        var product = postJson(token, "/api/products", """
            {"sku":"HIST-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var salesWarehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"HIST-SALE\"}");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"HIST-PUR\"}");
        postJson(token, "/api/stock/adjustment", """
            {"productId":"%s","warehouseId":"%s","quantityDelta":20,"rate":10,"notes":"Transaction history stock"}
            """.formatted(product.get("id").asText(), salesWarehouse.get("id").asText()));
        var sessionId = createSession(token, "Transaction dry run").get("id").asText();
        upload(token, sessionId, fixture("purchase.xml"), fixture("sales.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{}");

        assertThat(dryRun.get("strategy").asText()).isEqualTo("TRANSACTION_HISTORY");
        assertThat(dryRun.get("summary").get("purchaseInvoicesPreviewed").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("salesInvoicesPreviewed").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("stockMovementsPreviewed").asInt()).isEqualTo(2);
    }

    @Test
    void dryRunDetectsVoucherAlreadyPresentInTenant() throws Exception {
        var registration = register("smart-dry-run-voucher-duplicate");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"DUP-VOUCHER-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"DUP-VOUCHER-WH\"}");
        var customer = postJson(token, "/api/customers", "{\"name\":\"Sample Customer\",\"creditLimit\":0}");
        var existing = new SalesInvoice();
        existing.tenantId = tenantId;
        existing.customerId = UUID.fromString(customer.get("id").asText());
        existing.invoiceNumber = "S-001";
        existing.invoiceDate = java.time.LocalDate.of(2026, 6, 15);
        existing.totalAmount = java.math.BigDecimal.valueOf(30);
        salesInvoices.save(existing);
        var sessionId = createSession(token, "Duplicate voucher dry run").get("id").asText();
        upload(token, sessionId, fixture("sales.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");

        assertThat(dryRun.get("summary").get("duplicateVouchersSkipped").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("salesInvoicesPreviewed").asInt()).isZero();
        assertThat(dryRun.get("summary").get("blockingErrors").asInt()).isZero();
        assertThat(dryRun.get("summary").get("reviewRequiredCount").asInt()).isZero();
        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(committed.get("summary").get("duplicateSalesVouchersSkipped").asInt()).isEqualTo(1);
    }

    @Test
    void rerunningDryRunReplacesPreviousRunAndItems() throws Exception {
        var registration = register("smart-dry-run-replace");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var sessionId = createSession(token, "Replace dry run").get("id").asText();
        upload(token, sessionId, fixture("inventory_master.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var first = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");
        var second = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");

        assertThat(second.get("id").asText()).isNotEqualTo(first.get("id").asText());
        assertThat(dryRuns.findFirstByTenantIdAndImportSessionIdOrderByStartedAtDesc(tenantId, UUID.fromString(sessionId))).isPresent();
        var itemPage = getJson(token, "/api/import-sessions/" + sessionId + "/dry-run/items?size=100");
        assertThat(itemPage.get("content").size()).isGreaterThan(0);
        assertThat(itemPage.get("content").findValuesAsText("id")).doesNotHaveDuplicates();
    }

    @Test
    void dryRunIsTenantScopedAndCustomerUserIsDenied() throws Exception {
        var registrationA = register("smart-dry-run-tenant-a");
        var tokenA = registrationA.get("accessToken").asText();
        var sessionId = createSession(tokenA, "Tenant A dry run").get("id").asText();
        upload(tokenA, sessionId, fixture("inventory_master.xml"));
        postJson(tokenA, "/api/import-sessions/" + sessionId + "/classify");
        postJson(tokenA, "/api/import-sessions/" + sessionId + "/stage");
        postJson(tokenA, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");

        var tokenB = register("smart-dry-run-tenant-b").get("accessToken").asText();
        mvc.perform(get("/api/import-sessions/{id}/dry-run", sessionId).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound());
        var customerToken = tokenForRole(UUID.fromString(registrationA.get("tenantId").asText()), DomainEnums.Role.CUSTOMER_USER);
        mvc.perform(post("/api/import-sessions/{id}/dry-run", sessionId)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void negativeSnapshotDryRunAppliesBlockAndSkipPoliciesWithoutChangingStock() throws Exception {
        var token = register("smart-dry-run-negative").get("accessToken").asText();
        postJson(token, "/api/products", """
            {"sku":"NEG-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"NEG-MAIN\"}");
        var sessionId = createSession(token, "Negative snapshot dry run").get("id").asText();
        upload(token, sessionId, fixture("negative_closing_stock.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var movementCount = stockMovements.count();

        var blocked = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"negativeStockPolicy\":\"BLOCK\"}");
        assertThat(blocked.get("summary").get("negativeStockRowsBlocked").asInt()).isEqualTo(1);
        assertThat(blocked.get("summary").get("blockingErrors").asInt()).isGreaterThanOrEqualTo(1);

        var skipped = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"negativeStockPolicy\":\"SKIP_STOCK_MOVEMENT\"}");
        assertThat(skipped.get("summary").get("negativeStockRowsSkipped").asInt()).isEqualTo(1);
        assertThat(stockMovements.count()).isEqualTo(movementCount);
    }

    @Test
    void dryRunReportsZeroCostItemsAndSkippedLedgerLinesAsWarnings() throws Exception {
        var token = register("smart-dry-run-rates").get("accessToken").asText();
        var sessionId = createSession(token, "Rate dry run").get("id").asText();
        upload(token, sessionId, fixture("zero_cost_purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{}");

        assertThat(dryRun.get("summary").get("zeroCostItems").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("ledgerLinesSkipped").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("invalidRateRows").asInt()).isZero();
        assertThat(dryRun.get("summary").get("warnings").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void dryRunReportsUnmatchedCashbookAndAgeingRowsForReview() throws Exception {
        var token = register("smart-dry-run-comparison").get("accessToken").asText();
        var sessionId = createSession(token, "Comparison dry run").get("id").asText();
        upload(token, sessionId, fixture("cashbook.xml"), fixture("stock_ageing.csv"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");

        assertThat(dryRun.get("summary").get("cashbookRowsUnmatched").asInt()).isEqualTo(2);
        assertThat(dryRun.get("summary").get("ageingRowsUnmatched").asInt()).isEqualTo(2);
        assertThat(dryRun.get("summary").get("reviewRequiredCount").asInt()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void safeCommitRequiresCompletedFreshDryRunAndExactConfirmation() throws Exception {
        var token = register("smart-commit-preflight").get("accessToken").asText();
        var sessionId = createSession(token, "Commit preflight").get("id").asText();

        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dryRunId\":\"" + UUID.randomUUID() + "\",\"confirmation\":\"COMMIT IMPORT PLAN\"}"))
            .andExpect(status().isBadRequest());

        upload(token, sessionId, fixture("inventory_master.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");

        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dryRunId\":\"" + dryRun.get("id").asText() + "\",\"confirmation\":\"commit\"}"))
            .andExpect(status().isBadRequest());

        var tenantId = UUID.fromString(getJson(token, "/api/auth/me").get("tenantId").asText());
        var file = importSessionFiles.findByTenantIdAndImportSessionIdOrderByCreatedAtAsc(tenantId, UUID.fromString(sessionId)).getFirst();
        file.detectionReason = file.detectionReason + " changed";
        importSessionFiles.save(file);
        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(dryRun.get("id").asText())))
            .andExpect(status().isConflict());
    }

    @Test
    void safeCommitCreatesMasterDataAndIsIdempotent() throws Exception {
        var registration = register("smart-commit-master");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var sessionId = createSession(token, "Master foundation").get("id").asText();
        upload(token, sessionId, fixture("inventory_master.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");

        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(committed.get("status").asText()).isEqualTo("COMMITTED");
        assertThat(committed.get("summary").get("productsCreated").asInt()).isEqualTo(2);
        assertThat(products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)).hasSize(2);
        var effectCount = smartEffects.findByTenantIdAndCommitIdOrderByCreatedAtAsc(
            tenantId, UUID.fromString(committed.get("id").asText())).size();
        assertThat(effectCount).isGreaterThan(0);

        var retry = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(retry.get("idempotentRetry").asBoolean()).isTrue();
        assertThat(products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)).hasSize(2);
        assertThat(smartEffects.findByTenantIdAndCommitIdOrderByCreatedAtAsc(
            tenantId, UUID.fromString(committed.get("id").asText()))).hasSize(effectCount);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("SMART_IMPORT_COMMIT_STARTED", "SMART_IMPORT_COMMITTED", "SMART_IMPORT_EFFECT_CREATED");
    }

    @Test
    void snapshotCommitUsesDeltaAndRepeatedSnapshotCreatesNoMovement() throws Exception {
        var registration = register("smart-commit-snapshot");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var product = postJson(token, "/api/products", """
            {"sku":"COMMIT-SNAP","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"COMMIT-MAIN\"}");
        saveMovementAt(tenantId, product, warehouse, "40", java.time.Instant.parse("2026-06-29T12:00:00Z"));

        var firstSession = createSession(token, "First snapshot commit").get("id").asText();
        upload(token, firstSession, fixture("closing_stock_full_company.xml"));
        postJson(token, "/api/import-sessions/" + firstSession + "/classify");
        postJson(token, "/api/import-sessions/" + firstSession + "/stage");
        var firstDryRun = postJson(token, "/api/import-sessions/" + firstSession + "/dry-run", "{}");
        var firstCommit = postJson(token, "/api/import-sessions/" + firstSession + "/commit", commitBody(firstDryRun.get("id").asText()));
        assertThat(firstCommit.get("summary").get("snapshotMovementsCreated").asInt()).isEqualTo(1);
        assertThat(firstCommit.get("summary").get("snapshotPositiveAdjustments").asInt()).isEqualTo(1);
        assertThat(stockMovements.currentStock(tenantId, UUID.fromString(product.get("id").asText()), UUID.fromString(warehouse.get("id").asText())))
            .isEqualByComparingTo("100");
        var movementCount = stockMovements.count();

        var secondSession = createSession(token, "Repeated snapshot commit").get("id").asText();
        upload(token, secondSession, fixture("closing_stock_full_company.xml"));
        postJson(token, "/api/import-sessions/" + secondSession + "/classify");
        postJson(token, "/api/import-sessions/" + secondSession + "/stage");
        var secondDryRun = postJson(token, "/api/import-sessions/" + secondSession + "/dry-run", "{}");
        var secondCommit = postJson(token, "/api/import-sessions/" + secondSession + "/commit", commitBody(secondDryRun.get("id").asText()));
        assertThat(secondCommit.get("summary").get("snapshotRowsNoChange").asInt()).isEqualTo(1);
        assertThat(secondCommit.get("summary").get("snapshotMovementsCreated").asInt()).isZero();
        assertThat(stockMovements.count()).isEqualTo(movementCount);
    }

    @Test
    void phase4B1CommitsSalesAndPurchaseInvoicesWithoutStockMovementsAndIsIdempotent() throws Exception {
        var registration = register("smart-commit-future-phase");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"VOUCHER-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"V-SALE\"}");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"V-PUR\"}");
        var sessionId = createSession(token, "Future phase rows").get("id").asText();
        upload(token, sessionId, fixture("sales.xml"), fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
        var movementCount = stockMovements.count();

        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(committed.get("status").asText()).isEqualTo("COMMITTED");
        assertThat(committed.get("summary").get("salesInvoicesCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("salesInvoiceItemsCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("purchaseInvoicesCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("purchaseInvoiceItemsCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("voucherStockMovementsCreated").asInt()).isZero();
        assertThat(committed.get("summary").get("stockMovementsSkippedDueToInvoiceOnly").asInt()).isEqualTo(2);
        assertThat(committed.get("summary").get("partiesCreated").asInt()).isEqualTo(2);
        assertThat(salesInvoices.findByTenantId(tenantId)).hasSize(1);
        assertThat(purchaseInvoices.findByTenantId(tenantId)).hasSize(1);
        assertThat(salesInvoiceItems.findByTenantId(tenantId)).hasSize(1);
        assertThat(purchaseInvoiceItems.findByTenantId(tenantId)).hasSize(1);
        assertThat(stockMovements.count()).isEqualTo(movementCount);

        var retry = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(retry.get("idempotentRetry").asBoolean()).isTrue();
        assertThat(salesInvoices.findByTenantId(tenantId)).hasSize(1);
        assertThat(purchaseInvoices.findByTenantId(tenantId)).hasSize(1);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("SMART_IMPORT_SALES_INVOICES_COMMITTED", "SMART_IMPORT_PURCHASE_INVOICES_COMMITTED");
    }

    @Test
    void repeatedInvoiceOnlyVoucherImportSkipsExactDuplicates() throws Exception {
        var registration = register("smart-commit-voucher-duplicate");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"VOUCHER-DUP","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"V-DUP-WH\"}");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"V-DUP-PUR\"}");

        var firstSession = createSession(token, "First voucher history").get("id").asText();
        upload(token, firstSession, fixture("sales.xml"), fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + firstSession + "/classify");
        postJson(token, "/api/import-sessions/" + firstSession + "/stage");
        var firstDryRun = postJson(token, "/api/import-sessions/" + firstSession + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
        postJson(token, "/api/import-sessions/" + firstSession + "/commit", commitBody(firstDryRun.get("id").asText()));

        var secondSession = createSession(token, "Repeated voucher history").get("id").asText();
        upload(token, secondSession, fixture("sales.xml"), fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + secondSession + "/classify");
        postJson(token, "/api/import-sessions/" + secondSession + "/stage");
        var secondDryRun = postJson(token, "/api/import-sessions/" + secondSession + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
        var secondCommit = postJson(token, "/api/import-sessions/" + secondSession + "/commit",
            commitBody(secondDryRun.get("id").asText()));

        assertThat(secondCommit.get("summary").get("duplicateSalesVouchersSkipped").asInt()).isEqualTo(1);
        assertThat(secondCommit.get("summary").get("duplicatePurchaseVouchersSkipped").asInt()).isEqualTo(1);
        assertThat(secondCommit.get("summary").get("salesInvoicesCreated").asInt()).isZero();
        assertThat(secondCommit.get("summary").get("purchaseInvoicesCreated").asInt()).isZero();
        assertThat(salesInvoices.findByTenantId(tenantId)).hasSize(1);
        assertThat(salesInvoiceItems.findByTenantId(tenantId)).hasSize(1);
        assertThat(purchaseInvoices.findByTenantId(tenantId)).hasSize(1);
        assertThat(purchaseInvoiceItems.findByTenantId(tenantId)).hasSize(1);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("SMART_IMPORT_DUPLICATE_VOUCHERS_SKIPPED");
    }

    @Test
    void phase4B2ACommitsCreditAndDebitNotesAsFinancialAdjustmentsWithoutStockMovements() throws Exception {
        var registration = register("smart-financial-notes");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"NOTE-A","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"NOTE-SALE\"}");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"NOTE-PURCHASE\"}");
        var sessionId = createSession(token, "Credit and debit notes").get("id").asText();
        upload(token, sessionId, fixture("credit_note.xml"), fixture("debit_note.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var staging = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(staging.get("summary").get("creditNotesStaged").asInt()).isEqualTo(1);
        assertThat(staging.get("summary").get("debitNotesStaged").asInt()).isEqualTo(1);
        assertThat(staging.get("summary").get("ledgerAdjustmentLinesStaged").asInt()).isEqualTo(8);
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
        assertThat(dryRun.get("summary").get("creditNotesPreviewed").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("debitNotesPreviewed").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("taxLinesCaptured").asInt()).isEqualTo(3);
        assertThat(dryRun.get("summary").get("discountLinesCaptured").asInt()).isEqualTo(1);
        assertThat(dryRun.get("summary").get("freightLinesCaptured").asInt()).isEqualTo(2);
        assertThat(dryRun.get("summary").get("roundOffLinesCaptured").asInt()).isEqualTo(2);
        var movementCount = stockMovements.count();

        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));

        assertThat(committed.get("status").asText()).isEqualTo("COMMITTED");
        assertThat(committed.get("summary").get("creditNotesCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("creditNoteItemsCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("debitNotesCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("debitNoteItemsCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("stockMovementsCreatedFromReturns").asInt()).isZero();
        assertThat(committed.get("summary").get("returnStockMovementsDeferred").asInt()).isEqualTo(2);
        assertThat(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(tenantId)).hasSize(2)
            .extracting(row -> row.adjustmentType)
            .containsExactlyInAnyOrder(DomainEnums.FinancialAdjustmentType.CREDIT_NOTE, DomainEnums.FinancialAdjustmentType.DEBIT_NOTE);
        assertThat(financialAdjustmentItems.findByTenantId(tenantId)).hasSize(2);
        assertThat(stockMovements.count()).isEqualTo(movementCount);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("SMART_IMPORT_CREDIT_NOTES_COMMITTED", "SMART_IMPORT_DEBIT_NOTES_COMMITTED",
                "SMART_IMPORT_LEDGER_ADJUSTMENTS_CAPTURED");

        var retry = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(retry.get("idempotentRetry").asBoolean()).isTrue();
        assertThat(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(tenantId)).hasSize(2);
        assertThat(financialAdjustmentItems.findByTenantId(tenantId)).hasSize(2);
    }

    @Test
    void creditNoteIdentityAndCommitRemainTenantIsolated() throws Exception {
        var first = register("smart-note-tenant-a");
        var second = register("smart-note-tenant-b");

        for (var registration : java.util.List.of(first, second)) {
            var token = registration.get("accessToken").asText();
            postJson(token, "/api/products", """
                {"sku":"TENANT-NOTE","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
                """);
            postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"TENANT-NOTE-WH\"}");
            var sessionId = createSession(token, "Tenant-isolated credit note").get("id").asText();
            upload(token, sessionId, fixture("credit_note.xml"));
            postJson(token, "/api/import-sessions/" + sessionId + "/classify");
            postJson(token, "/api/import-sessions/" + sessionId + "/stage");
            var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
                "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
            var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit",
                commitBody(dryRun.get("id").asText()));
            assertThat(committed.get("summary").get("creditNotesCreated").asInt()).isEqualTo(1);
            assertThat(committed.get("summary").get("duplicateCreditNotesSkipped").asInt()).isZero();
        }

        var firstTenant = UUID.fromString(first.get("tenantId").asText());
        var secondTenant = UUID.fromString(second.get("tenantId").asText());
        assertThat(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(firstTenant)).hasSize(1);
        assertThat(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(secondTenant)).hasSize(1);
        assertThat(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(firstTenant).getFirst().id)
            .isNotEqualTo(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(secondTenant).getFirst().id);
    }

    @Test
    void invoiceOnlyCommitStoresTaxDiscountFreightAndRoundOffOnSalesAndPurchaseInvoices() throws Exception {
        var registration = register("smart-invoice-financial-components");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"FIN-COMP","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"FIN-SALE\"}");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"FIN-PURCHASE\"}");
        var sessionId = createSession(token, "Invoice financial components").get("id").asText();
        upload(token, sessionId, fixture("sales_financial_components.xml"), fixture("purchase_financial_components.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
        var movementCount = stockMovements.count();
        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));

        var sale = salesInvoices.findByTenantIdAndInvoiceNumberIgnoreCase(tenantId, "S-FIN-001").orElseThrow();
        assertThat(sale.taxAmount).isEqualByComparingTo("5.40");
        assertThat(sale.discountAmount).isEqualByComparingTo("1.00");
        assertThat(sale.freightAmount).isEqualByComparingTo("3.50");
        assertThat(sale.roundOffAmount).isEqualByComparingTo("0.30");
        assertThat(sale.totalAmount).isEqualByComparingTo("38.20");
        var purchase = purchaseInvoices.findByTenantIdAndInvoiceNumberIgnoreCase(tenantId, "P-FIN-001").orElseThrow();
        assertThat(purchase.taxAmount).isEqualByComparingTo("5.40");
        assertThat(purchase.discountAmount).isEqualByComparingTo("0.50");
        assertThat(purchase.freightAmount).isEqualByComparingTo("3.50");
        assertThat(purchase.roundOffAmount).isEqualByComparingTo("0.10");
        assertThat(purchase.totalAmount).isEqualByComparingTo("38.50");
        assertThat(committed.get("summary").get("taxLinesCaptured").asInt()).isEqualTo(2);
        assertThat(committed.get("summary").get("discountLinesCaptured").asInt()).isEqualTo(2);
        assertThat(committed.get("summary").get("freightLinesCaptured").asInt()).isEqualTo(2);
        assertThat(committed.get("summary").get("roundOffLinesCaptured").asInt()).isEqualTo(2);
        assertThat(stockMovements.count()).isEqualTo(movementCount);
    }

    @Test
    void repeatedCreditAndDebitNotesAreSkippedWithoutDuplicateItems() throws Exception {
        var registration = register("smart-note-duplicates");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"NOTE-DUP","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"NOTE-DUP-S\"}");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"NOTE-DUP-P\"}");
        for (var index = 0; index < 2; index++) {
            var sessionId = createSession(token, "Note duplicate pass " + index).get("id").asText();
            upload(token, sessionId, fixture("credit_note.xml"), fixture("debit_note.xml"));
            postJson(token, "/api/import-sessions/" + sessionId + "/classify");
            postJson(token, "/api/import-sessions/" + sessionId + "/stage");
            var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
                "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
            var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
            if (index == 1) {
                assertThat(committed.get("summary").get("duplicateCreditNotesSkipped").asInt()).isEqualTo(1);
                assertThat(committed.get("summary").get("duplicateDebitNotesSkipped").asInt()).isEqualTo(1);
                assertThat(committed.get("summary").get("creditNotesCreated").asInt()).isZero();
                assertThat(committed.get("summary").get("debitNotesCreated").asInt()).isZero();
            }
        }
        assertThat(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(tenantId)).hasSize(2);
        assertThat(financialAdjustmentItems.findByTenantId(tenantId)).hasSize(2);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("SMART_IMPORT_DUPLICATE_NOTES_SKIPPED");
    }

    @Test
    void phase4B1RejectsStockAffectingVoucherMode() throws Exception {
        var registration = register("smart-commit-unsupported-mode");
        var token = registration.get("accessToken").asText();
        postJson(token, "/api/products", """
            {"sku":"VOUCHER-MODE","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"V-MODE-WH\"}");
        var sessionId = createSession(token, "Unsupported stock mode").get("id").asText();
        upload(token, sessionId, fixture("sales.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"strategy\":\"TRANSACTION_HISTORY\",\"stockImpactMode\":\"CREATE_INVOICES_AND_STOCK_MOVEMENTS\"}");

        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(commitBody(dryRun.get("id").asText())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unresolvedCustomerAndSupplierBlockInvoiceOnlyCommit() throws Exception {
        for (var fixtureName : java.util.List.of("sales.xml", "purchase.xml", "credit_note.xml", "debit_note.xml")) {
            var registration = register("smart-unresolved-party-" + fixtureName.replace(".xml", ""));
            var token = registration.get("accessToken").asText();
            postJson(token, "/api/products", """
                {"sku":"UNRESOLVED-PARTY","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
                """);
            var warehouseName = fixtureName.startsWith("sales") || fixtureName.startsWith("credit") ? "Main Location" : "Main Godown";
            postJson(token, "/api/warehouses", "{\"name\":\"" + warehouseName + "\",\"code\":\"UNRES-" + fixtureName.charAt(0) + "\"}");
            var sessionId = createSession(token, "Unresolved party " + fixtureName).get("id").asText();
            upload(token, sessionId, fixture(fixtureName));
            postJson(token, "/api/import-sessions/" + sessionId + "/classify");
            postJson(token, "/api/import-sessions/" + sessionId + "/stage");
            var voucher = stagedVouchers.findByTenantIdAndImportSessionIdOrderByVoucherDateAscSourceRowNumberAsc(
                UUID.fromString(registration.get("tenantId").asText()), UUID.fromString(sessionId)).getFirst();
            voucher.matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
            voucher.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
            stagedVouchers.save(voucher);
            var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
                "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");

            mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(commitBody(dryRun.get("id").asText())))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void unresolvedCreditNoteProductBlocksFinancialAdjustmentCommit() throws Exception {
        var registration = register("smart-note-unresolved-product");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"NOTE-UNRES","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"NOTE-UNRES-WH\"}");
        var sessionId = createSession(token, "Unresolved note product").get("id").asText();
        upload(token, sessionId, fixture("credit_note.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var item = stagedVoucherItems.findByTenantIdAndImportSessionIdOrderBySourceRowNumberAsc(
            tenantId, UUID.fromString(sessionId)).getFirst();
        item.matchStatus = DomainEnums.SmartMatchStatus.UNKNOWN;
        item.reviewStatus = DomainEnums.SmartReviewStatus.PENDING;
        stagedVoucherItems.save(item);
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");

        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(commitBody(dryRun.get("id").asText())))
            .andExpect(status().isBadRequest());
        assertThat(financialAdjustments.findByTenantIdOrderByVoucherDateDesc(tenantId)).isEmpty();
    }

    @Test
    void safeCommitBlocksUnresolvedReviewAndCustomerUser() throws Exception {
        var registration = register("smart-commit-access");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"MAGGI-REVIEW","name":"MAGGI MASALA 70","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var sessionId = createSession(token, "Review blocked").get("id").asText();
        upload(token, sessionId, fixture("duplicate_products.csv"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");

        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(commitBody(dryRun.get("id").asText())))
            .andExpect(status().isBadRequest());

        var customerToken = tokenForRole(tenantId, DomainEnums.Role.CUSTOMER_USER);
        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON).content(commitBody(dryRun.get("id").asText())))
            .andExpect(status().isForbidden());
    }

    @Test
    void failedFoundationWriteRollsBackAllBusinessChanges() throws Exception {
        var registration = register("smart-commit-rollback");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var sessionId = createSession(token, "Rollback foundation").get("id").asText();
        upload(token, sessionId, fixture("inventory_master.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run", "{\"strategy\":\"TRANSACTION_HISTORY\"}");
        var productCount = products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).size();

        var conflict = new ExternalRecordMapping();
        conflict.tenantId = tenantId;
        conflict.sourceSystem = "SMART_IMPORT";
        conflict.entityType = "PRODUCT";
        conflict.normalizedKey = "SAMPLE ITEM A|PCS";
        conflict.localEntityType = "PRODUCT";
        conflict.localEntityId = UUID.randomUUID();
        conflict.lastSeenAt = java.time.Instant.now();
        externalMappings.save(conflict);

        var failed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("summary").get("transactionRolledBack").asBoolean()).isTrue();
        assertThat(products.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)).hasSize(productCount);
        var commit = smartCommits.findByTenantIdAndImportSessionId(tenantId, UUID.fromString(sessionId)).orElseThrow();
        assertThat(smartEffects.findByTenantIdAndCommitIdOrderByCreatedAtAsc(tenantId, commit.id)).isEmpty();
    }

    @Test
    void snapshotCommitHonorsSkipAndImportAsIsNegativeStockPolicies() throws Exception {
        var skipRegistration = register("smart-commit-negative-skip");
        var skipToken = skipRegistration.get("accessToken").asText();
        var skipTenant = UUID.fromString(skipRegistration.get("tenantId").asText());
        var skipProduct = postJson(skipToken, "/api/products", """
            {"sku":"NEG-SKIP","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var skipWarehouse = postJson(skipToken, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"NEG-SKIP-WH\"}");
        var skipSession = createSession(skipToken, "Skip negative snapshot").get("id").asText();
        upload(skipToken, skipSession, fixture("negative_closing_stock.xml"));
        postJson(skipToken, "/api/import-sessions/" + skipSession + "/classify");
        postJson(skipToken, "/api/import-sessions/" + skipSession + "/stage");
        var skipDryRun = postJson(skipToken, "/api/import-sessions/" + skipSession + "/dry-run", "{\"negativeStockPolicy\":\"SKIP_STOCK_MOVEMENT\"}");
        var skipped = postJson(skipToken, "/api/import-sessions/" + skipSession + "/commit", commitBody(skipDryRun.get("id").asText()));
        assertThat(skipped.get("status").asText()).isEqualTo("COMMITTED");
        assertThat(skipped.get("summary").get("negativeStockRowsSkipped").asInt()).isEqualTo(1);
        assertThat(stockMovements.currentStock(skipTenant, UUID.fromString(skipProduct.get("id").asText()), UUID.fromString(skipWarehouse.get("id").asText())))
            .isEqualByComparingTo("0");

        var importRegistration = register("smart-commit-negative-import");
        var importToken = importRegistration.get("accessToken").asText();
        var importTenant = UUID.fromString(importRegistration.get("tenantId").asText());
        var tenant = tenants.findById(importTenant).orElseThrow();
        tenant.allowNegativeStock = true;
        tenants.save(tenant);
        var importProduct = postJson(importToken, "/api/products", """
            {"sku":"NEG-IMPORT","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        var importWarehouse = postJson(importToken, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"NEG-IMPORT-WH\"}");
        var importSession = createSession(importToken, "Import negative snapshot").get("id").asText();
        upload(importToken, importSession, fixture("negative_closing_stock.xml"));
        postJson(importToken, "/api/import-sessions/" + importSession + "/classify");
        postJson(importToken, "/api/import-sessions/" + importSession + "/stage");
        var importDryRun = postJson(importToken, "/api/import-sessions/" + importSession + "/dry-run", "{\"negativeStockPolicy\":\"IMPORT_AS_IS\"}");
        var imported = postJson(importToken, "/api/import-sessions/" + importSession + "/commit", commitBody(importDryRun.get("id").asText()));
        assertThat(imported.get("status").asText()).isEqualTo("COMMITTED");
        assertThat(imported.get("summary").get("snapshotMovementsCreated").asInt()).isEqualTo(1);
        assertThat(stockMovements.currentStock(importTenant, UUID.fromString(importProduct.get("id").asText()), UUID.fromString(importWarehouse.get("id").asText())))
            .isEqualByComparingTo("-5");
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(importTenant).stream().map(log -> log.action))
            .contains("NEGATIVE_STOCK_ALLOWED");
    }

    @Test
    void safeCommitCreatesReviewedTallyGodownAndRemainsTenantScoped() throws Exception {
        var registration = register("smart-commit-warehouse");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var sessionId = createSession(token, "Create Tally godown").get("id").asText();
        upload(token, sessionId, fixture("purchase.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var workspace = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        JsonNode warehouseIssue = null;
        for (var item : workspace.get("reviewItems")) {
            if ("UNKNOWN_WAREHOUSE".equals(item.get("code").asText())) warehouseIssue = item;
        }
        assertThat(warehouseIssue).isNotNull();
        postJson(token, "/api/import-sessions/" + sessionId + "/review-items/" + warehouseIssue.get("id").asText() + "/resolve",
            "{\"action\":\"CREATE_WAREHOUSE_LATER\"}");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");

        var otherToken = register("smart-commit-other-tenant").get("accessToken").asText();
        mvc.perform(post("/api/import-sessions/{id}/commit", sessionId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(commitBody(dryRun.get("id").asText())))
            .andExpect(status().isNotFound());

        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        assertThat(committed.get("status").asText()).isEqualTo("COMMITTED");
        assertThat(committed.get("summary").get("warehousesCreated").asInt()).isEqualTo(1);
        assertThat(warehouses.findByTenantIdAndNameIgnoreCase(tenantId, "Main Godown")).isPresent();
    }

    @Test
    void debtorAndCreditorAnalysisCreatesOutstandingSnapshotsWithoutFakeInvoices() throws Exception {
        var registration = register("smart-outstanding-snapshots");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var sessionId = createSession(token, "Outstanding snapshots").get("id").asText();
        upload(token, sessionId, fixture("debtors_creditors.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var workspace = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(workspace.get("summary").get("debtorRowsStaged").asInt()).isEqualTo(1);
        assertThat(workspace.get("summary").get("creditorRowsStaged").asInt()).isEqualTo(1);
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"strategy\":\"TRANSACTION_HISTORY\"}");
        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit",
            commitBody(dryRun.get("id").asText()));

        assertThat(committed.get("summary").get("outstandingSnapshotsCreated").asInt()).isEqualTo(2);
        assertThat(committed.get("summary").get("debtorRowsProcessed").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("creditorRowsProcessed").asInt()).isEqualTo(1);
        assertThat(outstandingSnapshots.findByTenantIdAndSourceImportSessionIdOrderBySourceRowNumberAsc(
            tenantId, UUID.fromString(sessionId))).extracting(row -> row.outstandingAmount.toPlainString())
            .containsExactly("2500.00", "1800.00");
        assertThat(salesInvoices.findByTenantId(tenantId)).isEmpty();
        assertThat(purchaseInvoices.findByTenantId(tenantId)).isEmpty();
    }

    @Test
    void cashbookLinksReceiptsAndPaymentsToInvoiceOnlyDocumentsWithoutChangingStock() throws Exception {
        var registration = register("smart-cashbook-invoice-match");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/products", """
            {"sku":"CASH-MATCH","name":"Sample Item A","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":5}
            """);
        postJson(token, "/api/warehouses", "{\"name\":\"Main Location\",\"code\":\"CASH-SALE\"}");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"CASH-PURCHASE\"}");
        var sessionId = createSession(token, "Invoices and matched cashbook").get("id").asText();
        upload(token, sessionId, fixture("cashbook_matched.xml"), fixture("debtors_creditors.xml"),
            fixture("purchase.xml"), fixture("sales.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var workspace = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(workspace.get("cashbookEntries").findValuesAsText("cashbookMatchStatus"))
            .containsExactlyInAnyOrder("MATCHED_SALES_INVOICE", "MATCHED_PURCHASE_INVOICE");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"stockImpactMode\":\"CREATE_INVOICES_ONLY\"}");
        assertThat(dryRun.get("summary").get("cashbookRowsReady").asInt()).isEqualTo(2);
        var movementCount = stockMovements.count();
        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit",
            commitBody(dryRun.get("id").asText()));

        assertThat(committed.get("summary").get("customerPaymentsCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("supplierPaymentsCreated").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("paymentsLinkedToSalesInvoices").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("paymentsLinkedToPurchaseInvoices").asInt()).isEqualTo(1);
        assertThat(customerPayments.countByTenantIdAndSourceImportSessionId(tenantId, UUID.fromString(sessionId))).isEqualTo(1);
        assertThat(supplierPayments.countByTenantIdAndSourceImportSessionId(tenantId, UUID.fromString(sessionId))).isEqualTo(1);
        assertThat(customerPayments.findByTenantIdAndCustomerIdOrderByPaymentDateDesc(tenantId,
            customerPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId)).findFirst().orElseThrow().customerId)
            .getFirst().salesInvoiceId).isNotNull();
        assertThat(supplierPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId)).findFirst().orElseThrow().purchaseInvoiceId)
            .isNotNull();
        assertThat(stockMovements.count()).isEqualTo(movementCount);
    }

    @Test
    void unmatchedCashbookRowsRemainVisibleAndAreNotSilentlyPosted() throws Exception {
        var registration = register("smart-cashbook-unmatched-commit");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var sessionId = createSession(token, "Unmatched cashbook").get("id").asText();
        upload(token, sessionId, fixture("cashbook_unmatched.csv"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var workspace = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        assertThat(workspace.get("cashbookEntries").get(0).get("cashbookMatchStatus").asText())
            .isEqualTo("UNMATCHED_REVIEW");
        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"strategy\":\"TRANSACTION_HISTORY\"}");
        assertThat(dryRun.get("summary").get("reviewRequiredCount").asInt()).isZero();
        var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit",
            commitBody(dryRun.get("id").asText()));

        assertThat(committed.get("summary").get("cashbookRowsUnmatched").asInt()).isEqualTo(1);
        assertThat(committed.get("summary").get("customerPaymentsCreated").asInt()).isZero();
        assertThat(stagedCashbook.findByTenantIdAndImportSessionIdOrderByEntryDateAscSourceRowNumberAsc(
            tenantId, UUID.fromString(sessionId))).hasSize(1);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("SMART_IMPORT_CASHBOOK_REVIEW_REQUIRED");
    }

    @Test
    void ambiguousCashbookInvoiceMatchRequiresReviewInsteadOfGuessing() throws Exception {
        var token = register("smart-cashbook-low-confidence").get("accessToken").asText();
        var sessionId = createSession(token, "Ambiguous cashbook match").get("id").asText();
        upload(token, sessionId, fixture("cashbook_ambiguous.csv"), fixture("sales_ambiguous_amount.xml"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var workspace = postJson(token, "/api/import-sessions/" + sessionId + "/stage");

        assertThat(workspace.get("cashbookEntries").get(0).get("cashbookMatchStatus").asText())
            .isEqualTo("LOW_CONFIDENCE_REVIEW");
        assertThat(workspace.get("cashbookEntries").get(0).get("matchedInvoiceId").asText()).isBlank();
        assertThat(workspace.get("reviewItems").findValuesAsText("code"))
            .contains("CASHBOOK_LOW_CONFIDENCE");
    }

    @Test
    void repeatedCashbookImportSkipsDuplicatePaymentsAcrossSessions() throws Exception {
        var registration = register("smart-cashbook-idempotent");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/customers", "{\"name\":\"Sample Customer\",\"creditLimit\":10000}");
        postJson(token, "/api/suppliers", "{\"name\":\"Sample Supplier\",\"creditDays\":30}");

        for (var pass = 0; pass < 2; pass++) {
            var sessionId = createSession(token, "Cashbook pass " + pass).get("id").asText();
            upload(token, sessionId, fixture("cashbook_party_payments.csv"));
            postJson(token, "/api/import-sessions/" + sessionId + "/classify");
            postJson(token, "/api/import-sessions/" + sessionId + "/stage");
            var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
                "{\"strategy\":\"TRANSACTION_HISTORY\"}");
            var committed = postJson(token, "/api/import-sessions/" + sessionId + "/commit",
                commitBody(dryRun.get("id").asText()));
            if (pass == 0) {
                assertThat(committed.get("summary").get("customerPaymentsCreated").asInt()).isEqualTo(1);
                assertThat(committed.get("summary").get("supplierPaymentsCreated").asInt()).isEqualTo(1);
            } else {
                assertThat(committed.get("summary").get("duplicatePaymentsSkipped").asInt()).isEqualTo(2);
                assertThat(committed.get("summary").get("customerPaymentsCreated").asInt()).isZero();
                assertThat(committed.get("summary").get("supplierPaymentsCreated").asInt()).isZero();
            }
        }
        assertThat(customerPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId))).hasSize(1);
        assertThat(supplierPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId))).hasSize(1);
    }

    @Test
    void unmatchedCashbookCanBeResolvedToCustomerPaymentAndUpdatesCommittedReconciliation() throws Exception {
        var registration = register("cashbook-review-customer");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var customer = postJson(token, "/api/customers", "{\"name\":\"Resolved Customer\",\"creditLimit\":5000}");
        var review = stageUnmatchedCashbook(token, "Customer review");
        var sessionId = review.get("sessionId").asText();
        var entryId = review.get("entryId").asText();
        var candidates = getJson(token, "/api/import-sessions/" + sessionId + "/cashbook-review");
        assertThat(candidates.get(0).get("candidates").findValuesAsText("reason")).isNotEmpty();

        var dryRun = postJson(token, "/api/import-sessions/" + sessionId + "/dry-run",
            "{\"strategy\":\"TRANSACTION_HISTORY\"}");
        postJson(token, "/api/import-sessions/" + sessionId + "/commit", commitBody(dryRun.get("id").asText()));
        var resolved = postJson(token, "/api/import-sessions/" + sessionId + "/cashbook-review/" + entryId + "/resolve",
            cashbookResolution("MAP_CUSTOMER", customer.get("id").asText()));

        assertThat(resolved.get("paymentPosted").asBoolean()).isTrue();
        assertThat(customerPayments.countByTenantIdAndSourceImportSessionId(tenantId, UUID.fromString(sessionId))).isEqualTo(1);
        var reconciliation = getJson(token, "/api/import-sessions/" + sessionId + "/commit-result");
        assertThat(reconciliation.get("summary").get("cashbookRowsManuallyResolved").asInt()).isEqualTo(1);
        assertThat(reconciliation.get("summary").get("cashbookRowsUnmatched").asInt()).isZero();
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("CASHBOOK_REVIEW_RESOLVED", "PAYMENT_MANUALLY_MATCHED");
    }

    @Test
    void unmatchedCashbookCanBeResolvedToSupplierPayment() throws Exception {
        var registration = register("cashbook-review-supplier");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var supplier = postJson(token, "/api/suppliers", "{\"name\":\"Resolved Supplier\",\"creditDays\":30}");
        var review = stageUnmatchedCashbook(token, "Supplier review");
        var resolved = postJson(token, "/api/import-sessions/" + review.get("sessionId").asText()
                + "/cashbook-review/" + review.get("entryId").asText() + "/resolve",
            cashbookResolution("MAP_SUPPLIER", supplier.get("id").asText()));

        assertThat(resolved.get("paymentPosted").asBoolean()).isTrue();
        assertThat(supplierPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId)))
            .singleElement().satisfies(payment -> assertThat(payment.supplierId)
                .isEqualTo(UUID.fromString(supplier.get("id").asText())));
    }

    @Test
    void unmatchedCashbookCanBeResolvedToSalesInvoice() throws Exception {
        var registration = register("cashbook-review-sales-invoice");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var customer = postJson(token, "/api/customers", "{\"name\":\"Invoice Customer\",\"creditLimit\":5000}");
        var invoice = salesInvoice(tenantId, UUID.fromString(customer.get("id").asText()), "MAN-S-1");
        var review = stageUnmatchedCashbook(token, "Sales invoice review");
        postJson(token, "/api/import-sessions/" + review.get("sessionId").asText()
                + "/cashbook-review/" + review.get("entryId").asText() + "/resolve",
            cashbookResolution("MAP_SALES_INVOICE", invoice.id.toString()));

        assertThat(customerPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId)))
            .singleElement().satisfies(payment -> assertThat(payment.salesInvoiceId).isEqualTo(invoice.id));
    }

    @Test
    void unmatchedCashbookCanBeResolvedToPurchaseInvoice() throws Exception {
        var registration = register("cashbook-review-purchase-invoice");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var supplier = postJson(token, "/api/suppliers", "{\"name\":\"Invoice Supplier\",\"creditDays\":30}");
        var invoice = purchaseInvoice(tenantId, UUID.fromString(supplier.get("id").asText()), "MAN-P-1");
        var review = stageUnmatchedCashbook(token, "Purchase invoice review");
        postJson(token, "/api/import-sessions/" + review.get("sessionId").asText()
                + "/cashbook-review/" + review.get("entryId").asText() + "/resolve",
            cashbookResolution("MAP_PURCHASE_INVOICE", invoice.id.toString()));

        assertThat(supplierPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId)))
            .singleElement().satisfies(payment -> assertThat(payment.purchaseInvoiceId).isEqualTo(invoice.id));
    }

    @Test
    void manualCashbookResolutionPreventsDuplicatePaymentsAcrossSessions() throws Exception {
        var registration = register("cashbook-review-duplicate");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var customer = postJson(token, "/api/customers", "{\"name\":\"Duplicate Customer\",\"creditLimit\":5000}");
        for (var pass = 0; pass < 2; pass++) {
            var review = stageUnmatchedCashbook(token, "Duplicate resolution " + pass);
            var resolved = postJson(token, "/api/import-sessions/" + review.get("sessionId").asText()
                    + "/cashbook-review/" + review.get("entryId").asText() + "/resolve",
                cashbookResolution("MAP_CUSTOMER", customer.get("id").asText()));
            assertThat(resolved.get("duplicatePaymentSkipped").asBoolean()).isEqualTo(pass == 1);
        }
        assertThat(customerPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId))).hasSize(1);
    }

    @Test
    void cashbookReviewIsTenantScopedAndCustomerUserIsBlocked() throws Exception {
        var registration = register("cashbook-review-security");
        var ownerToken = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var customer = postJson(ownerToken, "/api/customers", "{\"name\":\"Secure Customer\",\"creditLimit\":5000}");
        var review = stageUnmatchedCashbook(ownerToken, "Secure review");
        var sessionId = review.get("sessionId").asText();
        var entryId = review.get("entryId").asText();
        var otherToken = register("cashbook-review-other-tenant").get("accessToken").asText();
        var customerToken = tokenForRole(tenantId, DomainEnums.Role.CUSTOMER_USER);

        mvc.perform(get("/api/import-sessions/{id}/cashbook-review", sessionId)
                .header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/import-sessions/{id}/cashbook-review/{entryId}/resolve", sessionId, entryId)
                .header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON)
                .content(cashbookResolution("MAP_CUSTOMER", customer.get("id").asText())))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/import-sessions/{id}/cashbook-review", sessionId)
                .header("Authorization", "Bearer " + customerToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/import-sessions/{id}/cashbook-review/{entryId}/resolve", sessionId, entryId)
                .header("Authorization", "Bearer " + customerToken).contentType(MediaType.APPLICATION_JSON)
                .content(cashbookResolution("MAP_CUSTOMER", customer.get("id").asText())))
            .andExpect(status().isForbidden());
    }

    @Test
    void ignoredCashbookEntryCreatesNoPayment() throws Exception {
        var registration = register("cashbook-review-ignore");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var review = stageUnmatchedCashbook(token, "Ignore review");
        postJson(token, "/api/import-sessions/" + review.get("sessionId").asText()
                + "/cashbook-review/" + review.get("entryId").asText() + "/resolve",
            "{\"action\":\"IGNORE\",\"note\":\"Non-business transfer\"}");

        assertThat(customerPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId))).isEmpty();
        assertThat(supplierPayments.findAll().stream().filter(row -> tenantId.equals(row.tenantId))).isEmpty();
        assertThat(getJson(token, "/api/import-sessions/" + review.get("sessionId").asText() + "/cashbook-review")).isEmpty();
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(log -> log.action))
            .contains("CASHBOOK_ENTRY_IGNORED");
    }

    private JsonNode stageUnmatchedCashbook(String token, String name) throws Exception {
        var sessionId = createSession(token, name).get("id").asText();
        upload(token, sessionId, fixture("cashbook_unmatched.csv"));
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        var workspace = postJson(token, "/api/import-sessions/" + sessionId + "/stage");
        var result = mapper.createObjectNode();
        result.put("sessionId", sessionId);
        result.put("entryId", workspace.get("cashbookEntries").get(0).get("id").asText());
        return result;
    }

    private String cashbookResolution(String action, String targetId) {
        return "{\"action\":\"" + action + "\",\"targetId\":\"" + targetId
            + "\",\"confirmation\":\"POST PAYMENT\"}";
    }

    private SalesInvoice salesInvoice(UUID tenantId, UUID customerId, String number) {
        var invoice = new SalesInvoice();
        invoice.tenantId = tenantId;
        invoice.customerId = customerId;
        invoice.warehouseId = UUID.randomUUID();
        invoice.invoiceNumber = number;
        invoice.invoiceDate = LocalDate.of(2026, 6, 18);
        invoice.subtotal = new BigDecimal("500.00");
        invoice.totalAmount = new BigDecimal("500.00");
        return salesInvoices.save(invoice);
    }

    private PurchaseInvoice purchaseInvoice(UUID tenantId, UUID supplierId, String number) {
        var invoice = new PurchaseInvoice();
        invoice.tenantId = tenantId;
        invoice.supplierId = supplierId;
        invoice.warehouseId = UUID.randomUUID();
        invoice.invoiceNumber = number;
        invoice.invoiceDate = LocalDate.of(2026, 6, 18);
        invoice.subtotal = new BigDecimal("500.00");
        invoice.totalAmount = new BigDecimal("500.00");
        return purchaseInvoices.save(invoice);
    }

    private JsonNode register(String prefix) throws Exception {
        var response = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Smart Import Test","businessMode":"HYBRID","email":"%s-%s@example.com","password":"password123","fullName":"Owner"}
                    """.formatted(prefix, System.nanoTime())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private JsonNode createSession(String token, String name) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/import-sessions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode upload(String token, String sessionId, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/import-sessions/{id}/files", sessionId);
        for (var file : files) {
            request.file(file);
        }
        request.header("Authorization", "Bearer " + token);
        return mapper.readTree(mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private MockMultipartFile fixture(String name) throws IOException {
        var resource = new ClassPathResource("import-fixtures/smart-session/" + name);
        var contentType = name.endsWith(".csv") ? "text/csv" : "application/xml";
        return new MockMultipartFile("files", name, contentType, resource.getInputStream());
    }

    private JsonNode getJson(String token, String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String token, String path) throws Exception {
        return mapper.readTree(mvc.perform(post(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String token, String path, String body) throws Exception {
        return mapper.readTree(mvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private String commitBody(String dryRunId) {
        return "{\"dryRunId\":\"" + dryRunId + "\",\"confirmation\":\"COMMIT IMPORT PLAN\"}";
    }

    private JsonNode uploadClassifyAndStage(String token, String name, MockMultipartFile... files) throws Exception {
        var sessionId = createSession(token, name).get("id").asText();
        upload(token, sessionId, files);
        postJson(token, "/api/import-sessions/" + sessionId + "/classify");
        return postJson(token, "/api/import-sessions/" + sessionId + "/stage");
    }

    private String tokenForRole(UUID tenantId, DomainEnums.Role role) {
        var user = new UserAccount();
        user.email = role.name().toLowerCase() + "-smart-" + System.nanoTime() + "@example.com";
        user.fullName = "Smart Import " + role.name();
        user.passwordHash = passwordEncoder.encode("password123");
        users.save(user);

        var membership = new UserTenantMembership();
        membership.tenantId = tenantId;
        membership.userId = user.id;
        membership.role = role;
        memberships.save(membership);
        return jwtService.issue(user.id, tenantId, user.email, role);
    }

    private void saveMovementAt(UUID tenantId, JsonNode product, JsonNode warehouse, String quantity, java.time.Instant movementDate) {
        var movement = new StockMovement();
        movement.tenantId = tenantId;
        movement.productId = UUID.fromString(product.get("id").asText());
        movement.warehouseId = UUID.fromString(warehouse.get("id").asText());
        movement.movementType = DomainEnums.MovementType.ADJUSTMENT;
        movement.quantity = new java.math.BigDecimal(quantity);
        movement.baseQuantity = new java.math.BigDecimal(quantity);
        movement.rate = java.math.BigDecimal.TEN;
        movement.totalValue = movement.baseQuantity.abs().multiply(movement.rate);
        movement.movementDate = movementDate;
        movement.notes = "Dry-run dated stock baseline";
        stockMovements.save(movement);
    }
}
