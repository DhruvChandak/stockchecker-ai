package com.stockpilot.ai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.config.JwtService;
import com.stockpilot.ai.domain.CustomerPriceList;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.domain.SalesOrder;
import com.stockpilot.ai.domain.UserAccount;
import com.stockpilot.ai.domain.UserTenantMembership;
import com.stockpilot.ai.repo.Repositories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Repositories.UserRepository users;
    @Autowired Repositories.MembershipRepository memberships;
    @Autowired Repositories.SalesOrderRepository salesOrders;
    @Autowired Repositories.CustomerPriceListRepository priceLists;
    @Autowired Repositories.StockMovementRepository stockMovements;
    @Autowired Repositories.AuditLogRepository auditLogs;
    @Autowired Repositories.TenantRepository tenants;
    @Autowired Repositories.ProductRepository productRepository;
    @Autowired Repositories.WarehouseRepository warehouseRepository;
    @Autowired Repositories.PurchaseInvoiceRepository purchaseInvoices;
    @Autowired Repositories.PurchaseInvoiceItemRepository purchaseInvoiceItems;
    @Autowired Repositories.SalesInvoiceRepository salesInvoices;
    @Autowired Repositories.SalesInvoiceItemRepository salesInvoiceItems;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @Test
    void purchaseIncreasesStockSaleReducesStockAndTenantIsolationHolds() throws Exception {
        var tokenA = register("a-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(tokenA, "/api/warehouses", "{\"name\":\"Main\",\"code\":\"MAIN\"}");
        var product = postJson(tokenA, "/api/products", """
            {"sku":"SKU1","name":"Test Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        var warehouseId = warehouse.get("id").asText();
        var productId = product.get("id").asText();

        postJson(tokenA, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P1","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":10,"rate":10}]}
            """.formatted(warehouseId, productId));
        var afterPurchase = mapper.readTree(mvc.perform(get("/api/stock/current")
                .param("productId", productId)
                .param("warehouseId", warehouseId)
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(afterPurchase.get(0).get("currentStock").asInt()).isEqualTo(10);

        postJson(tokenA, "/api/sales", """
            {"warehouseId":"%s","invoiceNumber":"S1","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":3,"rate":15}]}
            """.formatted(warehouseId, productId));
        var afterSale = mapper.readTree(mvc.perform(get("/api/stock/current")
                .param("productId", productId)
                .param("warehouseId", warehouseId)
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(afterSale.get(0).get("currentStock").asInt()).isEqualTo(7);

        var tokenB = register("b-" + System.nanoTime() + "@example.com");
        mvc.perform(get("/api/products/" + productId).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound());
    }

    @Test
    void duplicateSaleLinesCannotOversellAvailableStock() throws Exception {
        var token = register("oversell-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main\",\"code\":\"MAIN\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-OVERSELL","name":"Oversell Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        var warehouseId = warehouse.get("id").asText();
        var productId = product.get("id").asText();

        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-OVERSELL","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":5,"rate":10}]}
            """.formatted(warehouseId, productId));

        mvc.perform(post("/api/sales")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"warehouseId":"%s","invoiceNumber":"S-OVERSELL","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":3,"rate":15},{"productId":"%s","quantity":3,"rate":15}]}
                    """.formatted(warehouseId, productId, productId)))
            .andExpect(status().isBadRequest());

        var stock = mapper.readTree(mvc.perform(get("/api/stock/current")
                .param("productId", productId)
                .param("warehouseId", warehouseId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(stock.get(0).get("currentStock").asInt()).isEqualTo(5);
    }

    @Test
    void crossTenantSupplierAndCustomerIdsAreRejected() throws Exception {
        var tokenA = register("party-a-" + System.nanoTime() + "@example.com");
        var supplierA = postJson(tokenA, "/api/suppliers", "{\"name\":\"Other Tenant Supplier\"}");
        var customerA = postJson(tokenA, "/api/customers", "{\"name\":\"Other Tenant Customer\"}");

        var tokenB = register("party-b-" + System.nanoTime() + "@example.com");
        var warehouseB = postJson(tokenB, "/api/warehouses", "{\"name\":\"Main\",\"code\":\"MAIN\"}");
        var productB = postJson(tokenB, "/api/products", """
            {"sku":"SKU-TENANT","name":"Tenant Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);

        mvc.perform(post("/api/purchases")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"supplierId":"%s","warehouseId":"%s","invoiceNumber":"P-CROSS","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":1,"rate":10}]}
                    """.formatted(supplierA.get("id").asText(), warehouseB.get("id").asText(), productB.get("id").asText())))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/sales")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","warehouseId":"%s","invoiceNumber":"S-CROSS","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":1,"rate":15}]}
                    """.formatted(customerA.get("id").asText(), warehouseB.get("id").asText(), productB.get("id").asText())))
            .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantProductIdsAreRejectedInSalesAndPurchases() throws Exception {
        var tokenA = register("product-id-a-" + System.nanoTime() + "@example.com");
        var warehouseA = postJson(tokenA, "/api/warehouses", "{\"name\":\"Main A\",\"code\":\"MA\"}");

        var tokenB = register("product-id-b-" + System.nanoTime() + "@example.com");
        var productB = postJson(tokenB, "/api/products", """
            {"sku":"SKU-CROSS-PRODUCT-B","name":"Other Tenant Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);

        mvc.perform(post("/api/sales")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"warehouseId":"%s","invoiceNumber":"S-CROSS-PRODUCT","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":1,"rate":15}]}
                    """.formatted(warehouseA.get("id").asText(), productB.get("id").asText())))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/purchases")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"warehouseId":"%s","invoiceNumber":"P-CROSS-PRODUCT","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":1,"rate":10}]}
                    """.formatted(warehouseA.get("id").asText(), productB.get("id").asText())))
            .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantWarehouseIdIsRejectedInStockAdjustment() throws Exception {
        var tokenA = register("stock-warehouse-a-" + System.nanoTime() + "@example.com");
        var productA = postJson(tokenA, "/api/products", """
            {"sku":"SKU-STOCK-A","name":"Stock Tenant A Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);

        var tokenB = register("stock-warehouse-b-" + System.nanoTime() + "@example.com");
        var warehouseB = postJson(tokenB, "/api/warehouses", "{\"name\":\"Other Warehouse\",\"code\":\"OW\"}");

        mvc.perform(post("/api/stock/adjustment")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","warehouseId":"%s","quantityDelta":5,"rate":10,"notes":"Cross tenant warehouse should fail"}
                    """.formatted(productA.get("id").asText(), warehouseB.get("id").asText())))
            .andExpect(status().isNotFound());
    }

    @Test
    void transferMovesStockBetweenWarehouses() throws Exception {
        var token = register("transfer-" + System.nanoTime() + "@example.com");
        var source = postJson(token, "/api/warehouses", "{\"name\":\"Source\",\"code\":\"SRC\"}");
        var destination = postJson(token, "/api/warehouses", "{\"name\":\"Destination\",\"code\":\"DST\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-TRANSFER","name":"Transfer Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);

        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-TRANSFER","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":10,"rate":10}]}
            """.formatted(source.get("id").asText(), product.get("id").asText()));

        postJson(token, "/api/stock/transfer", """
            {"productId":"%s","sourceWarehouseId":"%s","destinationWarehouseId":"%s","quantity":4,"notes":"Move to retail counter"}
            """.formatted(product.get("id").asText(), source.get("id").asText(), destination.get("id").asText()));

        assertThat(stock(token, product.get("id").asText(), source.get("id").asText())).isEqualTo(6);
        assertThat(stock(token, product.get("id").asText(), destination.get("id").asText())).isEqualTo(4);
    }

    @Test
    void transferCreatesPairedMovementsAndRejectsInvalidTransfers() throws Exception {
        var registration = registerResponse("transfer-rules-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var source = postJson(token, "/api/warehouses", "{\"name\":\"Transfer Source\",\"code\":\"TRSRC\"}");
        var destination = postJson(token, "/api/warehouses", "{\"name\":\"Transfer Destination\",\"code\":\"TRDST\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-TRANSFER-RULES","name":"Transfer Rules Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-TRANSFER-RULES","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":5,"rate":10}]}
            """.formatted(source.get("id").asText(), product.get("id").asText()));

        mvc.perform(post("/api/stock/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","sourceWarehouseId":"%s","destinationWarehouseId":"%s","quantity":2,"notes":"Move for retail demand"}
                    """.formatted(product.get("id").asText(), source.get("id").asText(), destination.get("id").asText())))
            .andExpect(status().isOk());

        var transferMovements = stockMovements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, UUID.fromString(product.get("id").asText())).stream()
            .filter(movement -> "STOCK_TRANSFER".equals(movement.referenceType))
            .toList();
        assertThat(transferMovements).hasSize(2);
        assertThat(transferMovements).extracting(movement -> movement.movementType)
            .containsExactlyInAnyOrder(DomainEnums.MovementType.TRANSFER_OUT, DomainEnums.MovementType.TRANSFER_IN);
        assertThat(transferMovements.stream().map(movement -> movement.referenceId).distinct()).hasSize(1);

        mvc.perform(post("/api/stock/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","sourceWarehouseId":"%s","destinationWarehouseId":"%s","quantity":1,"notes":"Same warehouse should fail"}
                    """.formatted(product.get("id").asText(), source.get("id").asText(), source.get("id").asText())))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/stock/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","sourceWarehouseId":"%s","destinationWarehouseId":"%s","quantity":9,"notes":"Insufficient stock should fail"}
                    """.formatted(product.get("id").asText(), source.get("id").asText(), destination.get("id").asText())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void saleNegativeStockPolicyIsConsistent() throws Exception {
        var blocked = registerResponse("sale-negative-blocked-" + System.nanoTime() + "@example.com");
        var blockedToken = blocked.get("accessToken").asText();
        var blockedWarehouse = postJson(blockedToken, "/api/warehouses", "{\"name\":\"Sale Blocked Warehouse\",\"code\":\"SBW\"}");
        var blockedProduct = postJson(blockedToken, "/api/products", """
            {"sku":"SKU-SALE-BLOCKED","name":"Sale Blocked Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        mvc.perform(post("/api/sales")
                .header("Authorization", "Bearer " + blockedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"warehouseId":"%s","invoiceNumber":"S-NEG-BLOCKED","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":1,"rate":15}]}
                    """.formatted(blockedWarehouse.get("id").asText(), blockedProduct.get("id").asText())))
            .andExpect(status().isBadRequest());

        var allowed = registerResponse("sale-negative-allowed-" + System.nanoTime() + "@example.com");
        var allowedToken = allowed.get("accessToken").asText();
        var allowedTenantId = UUID.fromString(allowed.get("tenantId").asText());
        setAllowNegativeStock(allowedTenantId, true);
        var allowedWarehouse = postJson(allowedToken, "/api/warehouses", "{\"name\":\"Sale Allowed Warehouse\",\"code\":\"SAW\"}");
        var allowedProduct = postJson(allowedToken, "/api/products", """
            {"sku":"SKU-SALE-ALLOWED","name":"Sale Allowed Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        postJson(allowedToken, "/api/sales", """
            {"warehouseId":"%s","invoiceNumber":"S-NEG-ALLOWED","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":3,"rate":15}]}
            """.formatted(allowedWarehouse.get("id").asText(), allowedProduct.get("id").asText()));

        assertThat(stock(allowedToken, allowedProduct.get("id").asText(), allowedWarehouse.get("id").asText())).isEqualTo(-3);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(allowedTenantId))
            .extracting(log -> log.action)
            .contains("NEGATIVE_STOCK_ALLOWED", "SALES_INVOICE_CREATED", "STOCK_MOVEMENT_CREATED");
    }

    @Test
    void stockAdjustmentValidationAndNegativeStockPolicyAreConsistent() throws Exception {
        var registration = registerResponse("adjust-rules-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Adjust Warehouse\",\"code\":\"ADJ\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-ADJUST","name":"Adjust Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);

        mvc.perform(post("/api/stock/adjustment")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","warehouseId":"%s","quantityDelta":0,"rate":10,"notes":"No-op"}
                    """.formatted(product.get("id").asText(), warehouse.get("id").asText())))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/stock/adjustment")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","warehouseId":"%s","quantityDelta":1,"rate":10,"notes":""}
                    """.formatted(product.get("id").asText(), warehouse.get("id").asText())))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/stock/adjustment")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":"%s","warehouseId":"%s","quantityDelta":-1,"rate":10,"notes":"Shrinkage"}
                    """.formatted(product.get("id").asText(), warehouse.get("id").asText())))
            .andExpect(status().isBadRequest());

        setAllowNegativeStock(tenantId, true);
        postJson(token, "/api/stock/adjustment", """
            {"productId":"%s","warehouseId":"%s","quantityDelta":-2,"rate":10,"notes":"Owner approved temporary negative stock"}
            """.formatted(product.get("id").asText(), warehouse.get("id").asText()));

        assertThat(stock(token, product.get("id").asText(), warehouse.get("id").asText())).isEqualTo(-2);
        var movements = stockMovements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, UUID.fromString(product.get("id").asText())).stream()
            .filter(movement -> movement.movementType == DomainEnums.MovementType.ADJUSTMENT)
            .toList();
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).notes).contains("Owner approved temporary negative stock");
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId))
            .extracting(log -> log.action)
            .contains("STOCK_ADJUSTMENT", "NEGATIVE_STOCK_ALLOWED");
    }

    @Test
    void concurrentSalesCannotOversellWhenNegativeStockDisabled() throws Exception {
        var registration = registerResponse("concurrent-sale-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Concurrent Warehouse\",\"code\":\"CONC\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-CONCURRENT","name":"Concurrent Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-CONCURRENT","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":5,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> concurrentSaleStatus(start, token, warehouse.get("id").asText(), product.get("id").asText(), "S-CONCURRENT-1"));
            var second = executor.submit(() -> concurrentSaleStatus(start, token, warehouse.get("id").asText(), product.get("id").asText(), "S-CONCURRENT-2"));
            start.countDown();
            var statuses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(statuses).contains(200, 400);
            assertThat(stock(token, product.get("id").asText(), warehouse.get("id").asText())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void importStagesCommitsOpeningStockAndBlocksRecommit() throws Exception {
        var token = register("import-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var csv = "Item Name,Unit,Opening Stock,Purchase Price,Godown\nImported Test Product,PCS,11,7.5,Main Godown\n";
        var file = new MockMultipartFile("file", "sample-products.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(file)
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();
        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict());

        var products = mapper.readTree(mvc.perform(get("/api/products/search")
                .param("query", "Imported Test Product")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var productId = products.get("content").get(0).get("id").asText();
        assertThat(stock(token, productId, warehouse.get("id").asText())).isEqualTo(11);
    }

    @Test
    void undoPreviewAndUndoProductOnlyImportDeletesCreatedProduct() throws Exception {
        var token = register("import-undo-product-" + System.nanoTime() + "@example.com");
        var csv = "Item Name,Unit,Purchase Price\nRollback Only Product,PCS,7.5\n";
        var file = new MockMultipartFile("file", "rollback-products.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(file)
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();
        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        var preview = mapper.readTree(mvc.perform(get("/api/imports/" + batchId + "/undo-preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preview.get("canUndo").asBoolean()).isTrue();
        assertThat(preview.get("productsToDelete").toString()).contains("Rollback Only Product");

        var undo = mapper.readTree(mvc.perform(post("/api/imports/" + batchId + "/undo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"SAFE_REVERSAL\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(undo.get("status").asText()).isEqualTo("ROLLED_BACK");
        assertThat(undo.get("deletedProducts").asInt()).isEqualTo(1);

        var products = mapper.readTree(mvc.perform(get("/api/products/search")
                .param("query", "Rollback Only Product")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(products.get("content")).isEmpty();
    }

    @Test
    void undoOpeningStockImportCreatesReversalAndIsIdempotent() throws Exception {
        var token = register("import-undo-stock-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var csv = "Item Name,Unit,Opening Stock,Purchase Price,Godown\nRollback Stock Product,PCS,9,4.5,Main Godown\n";
        var file = new MockMultipartFile("file", "rollback-stock.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(file)
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();
        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        var productSearch = mapper.readTree(mvc.perform(get("/api/products/search")
                .param("query", "Rollback Stock Product")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var productId = productSearch.get("content").get(0).get("id").asText();
        assertThat(stock(token, productId, warehouse.get("id").asText())).isEqualTo(9);

        var preview = mapper.readTree(mvc.perform(get("/api/imports/" + batchId + "/undo-preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preview.get("stockMovementsToReverse").toString()).contains("Rollback Stock Product");

        mvc.perform(post("/api/imports/" + batchId + "/undo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"SAFE_REVERSAL\"}"))
            .andExpect(status().isOk());
        assertThat(stock(token, productId, warehouse.get("id").asText())).isEqualTo(0);

        var secondUndo = mapper.readTree(mvc.perform(post("/api/imports/" + batchId + "/undo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"SAFE_REVERSAL\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(secondUndo.get("warnings").toString()).contains("already rolled back");
        assertThat(stock(token, productId, warehouse.get("id").asText())).isEqualTo(0);
    }

    @Test
    void undoCannotAccessAnotherTenantBatchAndCustomerUsersCannotUndo() throws Exception {
        var ownerToken = register("import-undo-owner-" + System.nanoTime() + "@example.com");
        var tenantId = UUID.fromString(mapper.readTree(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("tenantId").asText());
        var csv = "Item Name,Unit\nTenant Scoped Rollback Product,PCS\n";
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "tenant-rollback.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();
        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk());

        var otherToken = register("import-undo-other-" + System.nanoTime() + "@example.com");
        mvc.perform(get("/api/imports/" + batchId + "/undo-preview").header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());

        var customer = postJson(ownerToken, "/api/customers", "{\"name\":\"Rollback Portal Customer\"}");
        var customerToken = customerPortalToken(tenantId, customer.get("id").asText(), "rollback-portal");
        mvc.perform(post("/api/imports/" + batchId + "/undo")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"SAFE_REVERSAL\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void devResetEndpointIsUnavailableByDefault() throws Exception {
        var token = register("dev-reset-disabled-" + System.nanoTime() + "@example.com");

        mvc.perform(post("/api/dev/current-tenant/reset-business-data")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"RESET WORKSPACE\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void importValidationReportsDuplicateInvoiceNumbers() throws Exception {
        var token = register("import-dup-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-IMPORT-DUP","name":"Import Duplicate Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-IMPORT-DUP-STOCK","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":10,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));
        postJson(token, "/api/sales", """
            {"warehouseId":"%s","invoiceNumber":"S-IMPORT-DUP","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":1,"rate":15}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));

        var csv = "Item Name,Qty,Rate,Godown,Voucher Type,Voucher No,Voucher Date\nImport Duplicate Product,1,15,Main Godown,Sales,S-IMPORT-DUP,2026-01-03\n";
        var file = new MockMultipartFile("file", "sample-sales.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(file)
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();
        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var errors = mapper.readTree(mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(errors.toString()).contains("DUPLICATE_INVOICE");
    }

    @Test
    void importValidationReportsProductQualityErrorsWithRowAndFieldDetails() throws Exception {
        var token = register("import-product-quality-" + System.nanoTime() + "@example.com");
        var csv = """
            Item Name,SKU,Unit,Opening Stock,Purchase Price
            ,SKU-MISSING,PCS,1,10
            Maggi A,SKU-DUP,PCS,1,10
            Maggi B,SKU-DUP,PCS,1,10
            No Unit Product,SKU-NOUNIT,,1,10
            """;
        var file = new MockMultipartFile("file", "bad-products.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(file)
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();
        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        var errors = mapper.readTree(mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(errors.toString())
            .contains("PRODUCT_NAME_REQUIRED")
            .contains("DUPLICATE_SKU")
            .contains("UNIT_MISSING")
            .contains("rowNumber")
            .contains("fieldName")
            .contains("suggestedFix");
    }

    @Test
    void importValidationDetectsDuplicateCustomerAndSupplierGstins() throws Exception {
        var token = register("import-party-quality-" + System.nanoTime() + "@example.com");
        var customersCsv = """
            Party Name,GSTIN,Ledger Group
            Ravi Traders,27ABCDE1234F1Z5,Sundry Debtors
            Ravi Branch,27ABCDE1234F1Z5,Sundry Debtors
            """;
        var customerUpload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "customers.csv", "text/csv", customersCsv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/imports/" + customerUpload.get("batchId").asText() + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var customerErrors = mvc.perform(get("/api/imports/" + customerUpload.get("batchId").asText() + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(customerErrors).contains("DUPLICATE_GSTIN").contains("CUSTOMER");

        var suppliersCsv = """
            Party Name,GSTIN,Ledger Group
            ABC Supplier,29ABCDE1234F1Z5,Sundry Creditors
            ABC Supplier Branch,29ABCDE1234F1Z5,Sundry Creditors
            """;
        var supplierUpload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "suppliers.csv", "text/csv", suppliersCsv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/imports/" + supplierUpload.get("batchId").asText() + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var supplierErrors = mvc.perform(get("/api/imports/" + supplierUpload.get("batchId").asText() + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(supplierErrors).contains("DUPLICATE_GSTIN").contains("SUPPLIER");
    }

    @Test
    void tallyDisplayStockXmlUploadStagesProductRows() throws Exception {
        var token = register("tally-display-stock-" + System.nanoTime() + "@example.com");
        var xml = """
            <ENVELOPE>
              <DSPACCNAME><DSPDISPNAME>Shape Inventory Item One</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL>
                <DSPCLQTY>30 Nos</DSPCLQTY>
                <DSPCLRATE>50.92</DSPCLRATE>
                <DSPCLAMTA>-1527.72</DSPCLAMTA>
              </DSPSTKCL></DSPSTKINFO>
              <DSPACCNAME><DSPDISPNAME>Shape Inventory Item Two</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL>
                <DSPCLQTY>12 Nos</DSPCLQTY>
                <DSPCLRATE>18.50</DSPCLRATE>
                <DSPCLAMTA>-222.00</DSPCLAMTA>
              </DSPSTKCL></DSPSTKINFO>
            </ENVELOPE>
            """;

        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "shape-inventory.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY_XML")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(upload.get("rowCount").asInt()).isEqualTo(2);
        var preview = mapper.readTree(mvc.perform(get("/api/imports/" + upload.get("batchId").asText() + "/preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preview.toString())
            .contains("Shape Inventory Item One")
            .contains("30")
            .contains("50.92")
            .contains("STOCK_SNAPSHOT")
            .contains("CREATE_NEW_PRODUCT")
            .contains("CREATE_SNAPSHOT_ADJUSTMENT");
    }

    @Test
    void tallyStockSnapshotReimportSameQuantityDoesNotDoubleStock() throws Exception {
        var registration = registerResponse("tally-snapshot-idempotent-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");

        var firstBatchId = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Shape Snapshot Product", "100 Nos", "20"));
        var firstResult = mvc.perform(post("/api/imports/" + firstBatchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(firstResult)
            .contains("\"productsCreated\":1")
            .contains("\"stockSnapshotRowsProcessed\":1")
            .contains("\"stockSnapshotAdjustmentsCreated\":1")
            .contains("\"stockSnapshotPositiveAdjustments\":1")
            .contains("\"openingBalanceMovementsCreated\":0")
            .contains("\"stockMovementsCreated\":1");

        var product = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "SHAPE SNAPSHOT PRODUCT").getFirst();
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(100);
        assertThat(stockMovements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, product.id)).hasSize(1);

        var secondBatchId = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("  Shape   Snapshot Product  ", "100 Nos", "20"));
        var preview = mvc.perform(get("/api/imports/" + secondBatchId + "/preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview)
            .contains("MATCH_EXISTING_PRODUCT")
            .contains("\"currentStock\":100")
            .contains("\"importedStock\":100")
            .contains("\"delta\":0")
            .contains("NO_CHANGE");

        var secondResult = mvc.perform(post("/api/imports/" + secondBatchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(secondResult)
            .contains("\"productsCreated\":0")
            .contains("\"productsMatchedExisting\":1")
            .contains("\"stockSnapshotRowsProcessed\":1")
            .contains("\"stockSnapshotRowsUnchanged\":1")
            .contains("\"stockSnapshotAdjustmentsCreated\":0")
            .contains("\"stockMovementsCreated\":0");
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(100);
        assertThat(stockMovements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, product.id)).hasSize(1);
        assertThat(productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "SHAPE SNAPSHOT PRODUCT")).hasSize(1);

        var reconciliation = mvc.perform(get("/api/imports/" + secondBatchId + "/reconciliation").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reconciliation)
            .contains("\"productsCreated\":0")
            .contains("\"productsMatchedExisting\":1")
            .contains("\"stockSnapshotRowsUnchanged\":1")
            .contains("\"stockMovementsCreated\":0");
    }

    @Test
    void snapshotThenInvoiceOnlyPurchaseAndSaleKeepCurrentStockUnchanged() throws Exception {
        var registration = registerResponse("snapshot-invoice-only-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var snapshotBatch = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Snapshot Voucher Product", "100 Nos", "10"));
        mvc.perform(post("/api/imports/" + snapshotBatch + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var product = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "SNAPSHOT VOUCHER PRODUCT").getFirst();
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(100);

        var purchaseXml = tallyVoucherXml("Purchase", "P-SNAPSHOT-HISTORY", "20260401", "Sample Supplier", "Snapshot Voucher Product", "100 Nos", "10/Nos");
        var purchaseBatch = uploadTallyVoucherXml(token, "purchase-history.xml", purchaseXml);
        var batch = mvc.perform(get("/api/imports/" + purchaseBatch).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(batch)
            .contains("\"voucherStockImpactMode\":\"CREATE_INVOICES_ONLY\"")
            .contains("\"hasStockSnapshot\":true")
            .contains("latestSnapshotDate");

        var invoiceOnlyBody = "{\"voucherStockImpactMode\":\"CREATE_INVOICES_ONLY\"}";
        mvc.perform(post("/api/imports/" + purchaseBatch + "/validate")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(invoiceOnlyBody))
            .andExpect(status().isOk());
        var validation = mvc.perform(get("/api/imports/" + purchaseBatch + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(validation).contains("VOUCHER_STOCK_MOVEMENTS_SKIPPED").doesNotContain("SNAPSHOT_STOCK_IMPACT_CONFIRMATION_REQUIRED");

        var purchaseResult = mvc.perform(post("/api/imports/" + purchaseBatch + "/commit")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(invoiceOnlyBody))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(purchaseResult)
            .contains("\"purchaseInvoicesCreated\":1")
            .contains("\"purchaseInvoiceItemsCreated\":1")
            .contains("\"stockMovementsCreated\":0")
            .contains("\"stockMovementsSkippedDueToInvoiceOnly\":1");
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(100);

        var salesXml = tallyVoucherXml("Sales", "S-SNAPSHOT-HISTORY", "20260402", "Sample Customer", "Snapshot Voucher Product", "10 Nos", "15/Nos");
        var salesBatch = uploadTallyVoucherXml(token, "sales-history.xml", salesXml);
        var salesResult = mvc.perform(post("/api/imports/" + salesBatch + "/commit")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(invoiceOnlyBody))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(salesResult)
            .contains("\"salesInvoicesCreated\":1")
            .contains("\"salesInvoiceItemsCreated\":1")
            .contains("\"stockMovementsCreated\":0")
            .contains("\"stockMovementsSkippedDueToInvoiceOnly\":1");
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(100);

        var invoice = purchaseInvoices.findByTenantId(tenantId).stream().filter(item -> "P-SNAPSHOT-HISTORY".equals(item.invoiceNumber)).findFirst().orElseThrow();
        assertThat(purchaseInvoiceItems.findByTenantIdAndPurchaseInvoiceId(tenantId, invoice.id)).hasSize(1);
        var salesInvoice = salesInvoices.findByTenantId(tenantId).stream().filter(item -> "S-SNAPSHOT-HISTORY".equals(item.invoiceNumber)).findFirst().orElseThrow();
        assertThat(salesInvoiceItems.findByTenantIdAndSalesInvoiceId(tenantId, salesInvoice.id)).hasSize(1);

        var reconciliation = mvc.perform(get("/api/imports/" + purchaseBatch + "/reconciliation").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reconciliation)
            .contains("\"voucherStockImpactMode\":\"CREATE_INVOICES_ONLY\"")
            .contains("\"stockMovementsSkippedDueToInvoiceOnly\":1")
            .contains("\"stockMovementsCreated\":0");

        var duplicateBatch = uploadTallyVoucherXml(token, "purchase-history-repeat.xml", purchaseXml);
        mvc.perform(post("/api/imports/" + duplicateBatch + "/commit")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(invoiceOnlyBody))
            .andExpect(status().isBadRequest());
        assertThat(purchaseInvoices.findByTenantId(tenantId).stream().filter(item -> "P-SNAPSHOT-HISTORY".equals(item.invoiceNumber))).hasSize(1);
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(100);
    }

    @Test
    void transactionHistoryModeAfterSnapshotRequiresAuditedConfirmation() throws Exception {
        var registration = registerResponse("snapshot-confirm-impact-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        mvc.perform(post("/api/imports/" + uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Confirmed Impact Product", "100 Nos", "10")) + "/commit")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var product = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "CONFIRMED IMPACT PRODUCT").getFirst();
        var batchId = uploadTallyVoucherXml(token, "confirmed-purchase.xml", tallyVoucherXml("Purchase", "P-CONFIRMED-IMPACT", "20260401", "Sample Supplier", "Confirmed Impact Product", "10 Nos", "10/Nos"));
        var transactionBody = "{\"voucherStockImpactMode\":\"CREATE_INVOICES_AND_STOCK_MOVEMENTS\"}";

        mvc.perform(post("/api/imports/" + batchId + "/validate")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(transactionBody))
            .andExpect(status().isOk());
        var errors = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(errors).contains("SNAPSHOT_STOCK_IMPACT_CONFIRMATION_REQUIRED");
        mvc.perform(post("/api/imports/" + batchId + "/commit")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(transactionBody))
            .andExpect(status().isBadRequest());

        var confirmedBody = "{\"voucherStockImpactMode\":\"CREATE_INVOICES_AND_STOCK_MOVEMENTS\",\"confirmStockMovementsAfterSnapshot\":true}";
        mvc.perform(post("/api/imports/" + batchId + "/commit")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(confirmedBody))
            .andExpect(status().isOk());
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(110);
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId)).anySatisfy(log -> assertThat(log.action).isEqualTo("VOUCHER_STOCK_IMPACT_OVERRIDE"));
    }

    @Test
    void postSnapshotModeAppliesOnlyVouchersAfterSnapshotDate() throws Exception {
        var registration = registerResponse("snapshot-date-impact-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        mvc.perform(post("/api/imports/" + uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Dated Snapshot Product", "100 Nos", "10")) + "/commit")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var product = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "DATED SNAPSHOT PRODUCT").getFirst();
        var beforeDate = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        var afterDate = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        var xml = "<ENVELOPE>"
            + tallyVoucherNode("Purchase", "P-BEFORE-SNAPSHOT", beforeDate, "Sample Supplier", "Dated Snapshot Product", "10 Nos", "10/Nos")
            + tallyVoucherNode("Purchase", "P-AFTER-SNAPSHOT", afterDate, "Sample Supplier", "Dated Snapshot Product", "5 Nos", "10/Nos")
            + "</ENVELOPE>";
        var batchId = uploadTallyVoucherXml(token, "dated-purchases.xml", xml);
        var body = "{\"voucherStockImpactMode\":\"APPLY_STOCK_MOVEMENTS_AFTER_SNAPSHOT_DATE\"}";
        var result = mvc.perform(post("/api/imports/" + batchId + "/commit")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(result)
            .contains("\"purchaseInvoicesCreated\":2")
            .contains("\"purchaseInvoiceItemsCreated\":2")
            .contains("\"voucherStockMovementsCreated\":1")
            .contains("\"stockMovementsSkippedBeforeSnapshotDate\":1");
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(105);
    }

    @Test
    void snapshotAwarenessIsTenantScoped() throws Exception {
        var tenantA = registerResponse("snapshot-context-a-" + System.nanoTime() + "@example.com");
        var tokenA = tenantA.get("accessToken").asText();
        postJson(tokenA, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        mvc.perform(post("/api/imports/" + uploadTallyDisplayStockXml(tokenA, tallyStockSnapshotXml("Tenant A Snapshot", "10 Nos", "10")) + "/commit")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk());

        var tenantB = registerResponse("snapshot-context-b-" + System.nanoTime() + "@example.com");
        var tokenB = tenantB.get("accessToken").asText();
        postJson(tokenB, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var batchId = uploadTallyVoucherXml(tokenB, "tenant-b-purchase.xml", tallyVoucherXml("Purchase", "P-TENANT-B", "20260601", "Sample Supplier", "Tenant B Product", "5 Nos", "10/Nos"));
        var batch = mvc.perform(get("/api/imports/" + batchId).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(batch)
            .contains("\"hasStockSnapshot\":false")
            .contains("\"voucherStockImpactMode\":\"CREATE_INVOICES_AND_STOCK_MOVEMENTS\"");
    }

    @Test
    void tallyStockSnapshotChangedQuantityCreatesOnlyDeltaAdjustments() throws Exception {
        var registration = registerResponse("tally-snapshot-delta-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");

        mvc.perform(post("/api/imports/" + uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Shape Delta Product", "100 Nos", "10")) + "/commit")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var product = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "SHAPE DELTA PRODUCT").getFirst();
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(100);

        var increasedBatchId = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Shape Delta Product", "120 Nos", "10"));
        var increasedResult = mvc.perform(post("/api/imports/" + increasedBatchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(increasedResult)
            .contains("\"stockSnapshotAdjustmentsCreated\":1")
            .contains("\"stockSnapshotPositiveAdjustments\":1")
            .contains("\"stockSnapshotNegativeAdjustments\":0")
            .contains("\"stockMovementsCreated\":1");
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(120);

        var decreasedBatchId = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Shape Delta Product", "80 Nos", "10"));
        var decreasedResult = mvc.perform(post("/api/imports/" + decreasedBatchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(decreasedResult)
            .contains("\"stockSnapshotAdjustmentsCreated\":1")
            .contains("\"stockSnapshotPositiveAdjustments\":0")
            .contains("\"stockSnapshotNegativeAdjustments\":1")
            .contains("\"stockMovementsCreated\":1");
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(80);

        var movements = stockMovements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, product.id);
        assertThat(movements)
            .hasSize(3)
            .allSatisfy(movement -> assertThat(movement.movementType).isEqualTo(DomainEnums.MovementType.ADJUSTMENT));
        assertThat(movements)
            .extracting(movement -> movement.baseQuantity)
            .anySatisfy(quantity -> assertThat(quantity).isEqualByComparingTo("20"))
            .anySatisfy(quantity -> assertThat(quantity).isEqualByComparingTo("-40"));
    }

    @Test
    void stockSnapshotMatchingIsTenantScoped() throws Exception {
        var tenantA = registerResponse("snapshot-tenant-a-" + System.nanoTime() + "@example.com");
        var tokenA = tenantA.get("accessToken").asText();
        var tenantAId = UUID.fromString(tenantA.get("tenantId").asText());
        postJson(tokenA, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        mvc.perform(post("/api/imports/" + uploadTallyDisplayStockXml(tokenA, tallyStockSnapshotXml("Cross Tenant Snapshot Product", "100 Nos", "10")) + "/commit")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk());

        var tenantB = registerResponse("snapshot-tenant-b-" + System.nanoTime() + "@example.com");
        var tokenB = tenantB.get("accessToken").asText();
        var tenantBId = UUID.fromString(tenantB.get("tenantId").asText());
        postJson(tokenB, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        mvc.perform(post("/api/imports/" + uploadTallyDisplayStockXml(tokenB, tallyStockSnapshotXml("Cross Tenant Snapshot Product", "50 Nos", "10")) + "/commit")
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk());

        assertThat(productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantAId, "CROSS TENANT SNAPSHOT PRODUCT")).hasSize(1);
        assertThat(productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantBId, "CROSS TENANT SNAPSHOT PRODUCT")).hasSize(1);
        var productA = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantAId, "CROSS TENANT SNAPSHOT PRODUCT").getFirst();
        var productB = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantBId, "CROSS TENANT SNAPSHOT PRODUCT").getFirst();
        assertThat(productA.id).isNotEqualTo(productB.id);
    }

    @Test
    void similarButNonExactSnapshotNameRequiresDuplicateReview() throws Exception {
        var registration = registerResponse("snapshot-similar-review-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        postJson(token, "/api/products", """
            {"sku":"MAGGI-MASALA-70","name":"Maggi Masala 70G","unitCode":"PCS","defaultPurchasePrice":8,"defaultSalesPrice":12,"reorderPoint":2}
            """);

        var batchId = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Maggi Masla 70G", "100 PCS", "8"));
        var preview = mvc.perform(get("/api/imports/" + batchId + "/preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview)
            .contains("POSSIBLE_DUPLICATE_REVIEW")
            .contains("BLOCKED_POSSIBLE_DUPLICATE_REVIEW")
            .contains("Similar existing product found: Maggi Masala 70G");

        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var errors = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(errors).contains("POSSIBLE_DUPLICATE_REVIEW");
        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        assertThat(productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "MAGGI MASLA 70G")).isEmpty();
    }

    @Test
    void sameSnapshotProductNameWithDifferentUnitDoesNotAutoMatch() throws Exception {
        var registration = registerResponse("snapshot-unit-review-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        postJson(token, "/api/products", """
            {"sku":"UNIT-SENSITIVE","name":"Unit Sensitive Product","unitCode":"PCS","defaultPurchasePrice":8,"defaultSalesPrice":12,"reorderPoint":2}
            """);

        var batchId = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Unit Sensitive Product", "25 BOX", "8"));
        var preview = mvc.perform(get("/api/imports/" + batchId + "/preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview)
            .contains("POSSIBLE_DUPLICATE_REVIEW")
            .contains("different unit")
            .contains("BLOCKED_POSSIBLE_DUPLICATE_REVIEW")
            .doesNotContain("MATCH_EXISTING_PRODUCT");

        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        assertThat(productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "UNIT SENSITIVE PRODUCT")).hasSize(1);
    }

    @Test
    void duplicateProductMergeRequiresExplicitConfirmation() throws Exception {
        var token = register("merge-confirm-" + System.nanoTime() + "@example.com");
        var target = postJson(token, "/api/products", """
            {"sku":"MERGE-TARGET","name":"Merge Confirm Product","unitCode":"PCS","defaultPurchasePrice":8,"defaultSalesPrice":12,"reorderPoint":2}
            """);
        var source = postJson(token, "/api/products", """
            {"sku":"MERGE-SOURCE","name":"Merge Confirm Product Copy","unitCode":"PCS","defaultPurchasePrice":8,"defaultSalesPrice":12,"reorderPoint":2}
            """);

        mvc.perform(post("/api/data-quality/products/merge")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetProductId":"%s","sourceProductIds":["%s"]}
                    """.formatted(target.get("id").asText(), source.get("id").asText())))
            .andExpect(status().isBadRequest());

        var result = mvc.perform(post("/api/data-quality/products/merge")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetProductId":"%s","sourceProductIds":["%s"],"confirmation":"MERGE PRODUCTS"}
                    """.formatted(target.get("id").asText(), source.get("id").asText())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(result).contains("\"mergedProducts\":1");
        assertThat(productRepository.findById(UUID.fromString(source.get("id").asText())).orElseThrow().active).isFalse();
    }

    @Test
    void negativeTallyDisplayStockBlockedByDefaultAndMissingCategoryIsOnlyWarning() throws Exception {
        var token = register("tally-negative-block-" + System.nanoTime() + "@example.com");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var batchId = uploadTallyDisplayStockXml(token, """
            <ENVELOPE>
              <DSPACCNAME><DSPDISPNAME>Positive Missing Category Product</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL><DSPCLQTY>4 Nos</DSPCLQTY><DSPCLRATE>10</DSPCLRATE></DSPSTKCL></DSPSTKINFO>
              <DSPACCNAME><DSPDISPNAME>Negative Block Product</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL><DSPCLQTY>-2 Nos</DSPCLQTY><DSPCLRATE>12</DSPCLRATE></DSPSTKCL></DSPSTKINFO>
            </ENVELOPE>
            """);

        mvc.perform(post("/api/imports/" + batchId + "/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"BLOCK\"}"))
            .andExpect(status().isOk());
        var errors = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(errors)
            .contains("NEGATIVE_QUANTITY")
            .contains("Negative stock quantity found")
            .contains("CATEGORY_MISSING")
            .contains("\"severity\":\"WARNING\"");

        mvc.perform(post("/api/imports/" + batchId + "/commit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"BLOCK\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void negativeTallyDisplayStockCanBeSkippedDuringCommit() throws Exception {
        var registration = registerResponse("tally-negative-skip-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var batchId = uploadTallyDisplayStockXml(token, """
            <ENVELOPE>
              <DSPACCNAME><DSPDISPNAME>Positive Skip Batch Product</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL><DSPCLQTY>9 Nos</DSPCLQTY><DSPCLRATE>11</DSPCLRATE></DSPSTKCL></DSPSTKINFO>
              <DSPACCNAME><DSPDISPNAME>Negative Skip Product</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL><DSPCLQTY>-5 Nos</DSPCLQTY><DSPCLRATE>13</DSPCLRATE></DSPSTKCL></DSPSTKINFO>
            </ENVELOPE>
            """);

        mvc.perform(post("/api/imports/" + batchId + "/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"SKIP_STOCK_MOVEMENT\"}"))
            .andExpect(status().isOk());
        var warnings = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(warnings).contains("NEGATIVE_STOCK_SKIPPED").doesNotContain("NEGATIVE_QUANTITY");

        var result = mvc.perform(post("/api/imports/" + batchId + "/commit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"SKIP_STOCK_MOVEMENT\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(result)
            .contains("\"negativeStockRowsFound\":1")
            .contains("\"negativeStockRowsSkipped\":1")
            .contains("\"openingBalanceMovementsCreated\":0")
            .contains("\"stockSnapshotRowsProcessed\":2")
            .contains("\"stockSnapshotAdjustmentsCreated\":1")
            .contains("\"stockMovementsCreated\":1")
            .contains("\"voucherStockMovementsCreated\":0");

        var products = mapper.readTree(mvc.perform(get("/api/products/search")
                .param("query", "Negative Skip Product")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var skippedProductId = products.get("content").get(0).get("id").asText();
        assertThat(stock(token, skippedProductId, warehouse.get("id").asText())).isEqualTo(0);
        assertThat(stockMovements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, UUID.fromString(skippedProductId))).isEmpty();

        var reconciliation = mvc.perform(get("/api/imports/" + batchId + "/reconciliation").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reconciliation)
            .contains("\"negativeStockRowsFound\":1")
            .contains("\"negativeStockRowsSkipped\":1")
            .contains("\"blockingErrors\":0");
    }

    @Test
    void negativeSnapshotSkipPolicyDoesNotAlterExistingStock() throws Exception {
        var registration = registerResponse("tally-negative-skip-existing-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        mvc.perform(post("/api/imports/" + uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Negative Skip Existing Product", "10 Nos", "10")) + "/commit")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var product = productRepository.findByTenantIdAndNormalizedNameIgnoreCase(tenantId, "NEGATIVE SKIP EXISTING PRODUCT").getFirst();
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(10);

        var batchId = uploadTallyDisplayStockXml(token, tallyStockSnapshotXml("Negative Skip Existing Product", "-5 Nos", "10"));
        var result = mvc.perform(post("/api/imports/" + batchId + "/commit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"SKIP_STOCK_MOVEMENT\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(result)
            .contains("\"negativeStockRowsSkipped\":1")
            .contains("\"stockSnapshotAdjustmentsCreated\":0")
            .contains("\"stockMovementsCreated\":0");
        assertThat(stock(token, product.id.toString(), warehouse.get("id").asText())).isEqualTo(10);
    }

    @Test
    void negativeTallyDisplayStockImportAsIsRequiresTenantSettingAndAuditsMovement() throws Exception {
        var blocked = registerResponse("tally-negative-as-is-block-" + System.nanoTime() + "@example.com");
        var blockedToken = blocked.get("accessToken").asText();
        postJson(blockedToken, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var blockedBatchId = uploadTallyDisplayStockXml(blockedToken, """
            <ENVELOPE>
              <DSPACCNAME><DSPDISPNAME>Negative As Is Blocked Product</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL><DSPCLQTY>-3 Nos</DSPCLQTY><DSPCLRATE>14</DSPCLRATE></DSPSTKCL></DSPSTKINFO>
            </ENVELOPE>
            """);
        mvc.perform(post("/api/imports/" + blockedBatchId + "/validate")
                .header("Authorization", "Bearer " + blockedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"IMPORT_AS_IS\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/imports/" + blockedBatchId + "/commit")
                .header("Authorization", "Bearer " + blockedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"IMPORT_AS_IS\"}"))
            .andExpect(status().isBadRequest());
        var blockedErrors = mvc.perform(get("/api/imports/" + blockedBatchId + "/errors").header("Authorization", "Bearer " + blockedToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(blockedErrors).contains("NEGATIVE_STOCK_NOT_ALLOWED");

        var allowed = registerResponse("tally-negative-as-is-allowed-" + System.nanoTime() + "@example.com");
        var allowedToken = allowed.get("accessToken").asText();
        var tenantId = UUID.fromString(allowed.get("tenantId").asText());
        setAllowNegativeStock(tenantId, true);
        var warehouse = postJson(allowedToken, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var allowedBatchId = uploadTallyDisplayStockXml(allowedToken, """
            <ENVELOPE>
              <DSPACCNAME><DSPDISPNAME>Negative As Is Allowed Product</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL><DSPCLQTY>-6 Nos</DSPCLQTY><DSPCLRATE>15</DSPCLRATE></DSPSTKCL></DSPSTKINFO>
            </ENVELOPE>
            """);
        mvc.perform(post("/api/imports/" + allowedBatchId + "/commit")
                .header("Authorization", "Bearer " + allowedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"IMPORT_AS_IS\"}"))
            .andExpect(status().isOk());

        var products = mapper.readTree(mvc.perform(get("/api/products/search")
                .param("query", "Negative As Is Allowed Product")
                .header("Authorization", "Bearer " + allowedToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var productId = products.get("content").get(0).get("id").asText();
        assertThat(stock(allowedToken, productId, warehouse.get("id").asText())).isEqualTo(-6);
        assertThat(stockMovements.findByTenantIdAndProductIdOrderByMovementDateDesc(tenantId, UUID.fromString(productId)))
            .anySatisfy(movement -> {
                assertThat(movement.movementType).isEqualTo(DomainEnums.MovementType.ADJUSTMENT);
                assertThat(movement.baseQuantity).isEqualByComparingTo("-6");
                assertThat(movement.referenceType).isEqualTo("IMPORT_BATCH");
            });
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(tenantId))
            .extracting(log -> log.action)
            .contains("NEGATIVE_STOCK_ALLOWED", "IMPORT_COMMITTED");
    }

    @Test
    void criticalProductImportErrorsStillBlockWhenNegativeStockPolicyWouldAllowCommit() throws Exception {
        var token = register("import-critical-skip-" + System.nanoTime() + "@example.com");
        var csv = """
            Item Name,Unit,Opening Stock,Purchase Price
            ,PCS,5,10
            """;
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "critical-products.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();

        mvc.perform(post("/api/imports/" + batchId + "/validate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"SKIP_STOCK_MOVEMENT\"}"))
            .andExpect(status().isOk());
        var errors = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(errors).contains("PRODUCT_NAME_REQUIRED");

        mvc.perform(post("/api/imports/" + batchId + "/commit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"negativeStockPolicy\":\"SKIP_STOCK_MOVEMENT\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void tallyXmlSalesVoucherWithMultipleLineItemsStagesEachLine() throws Exception {
        var token = register("tally-sales-stage-" + System.nanoTime() + "@example.com");
        var xml = """
            <ENVELOPE><BODY><DATA><TALLYMESSAGE>
              <VOUCHER VCHTYPE="Sales" ACTION="Create">
                <DATE>20260401</DATE>
                <VOUCHERNUMBER>S-TALLY-STAGE</VOUCHERNUMBER>
                <PARTYLEDGERNAME>Ravi Traders</PARTYLEDGERNAME>
                <ALLINVENTORYENTRIES.LIST>
                  <STOCKITEMNAME>Maggi 70g</STOCKITEMNAME>
                  <BILLEDQTY>2 PCS</BILLEDQTY>
                  <RATE>12/PCS</RATE>
                  <GODOWNNAME>Main Godown</GODOWNNAME>
                </ALLINVENTORYENTRIES.LIST>
                <ALLINVENTORYENTRIES.LIST>
                  <STOCKITEMNAME>Parle-G 250g</STOCKITEMNAME>
                  <BILLEDQTY>3 PCS</BILLEDQTY>
                  <RATE>22/PCS</RATE>
                  <GODOWNNAME>Main Godown</GODOWNNAME>
                </ALLINVENTORYENTRIES.LIST>
              </VOUCHER>
            </TALLYMESSAGE></DATA></BODY></ENVELOPE>
            """;
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "tally-sales.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        var preview = mapper.readTree(mvc.perform(get("/api/imports/" + upload.get("batchId").asText() + "/preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preview.toString()).contains("Maggi 70g").contains("Parle-G 250g");
    }

    @Test
    void tallySalesVoucherAutoCreatesMissingGodownWarehouse() throws Exception {
        var registration = registerResponse("tally-sales-godown-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var tenant = tenants.findById(tenantId).orElseThrow();
        tenant.allowNegativeStock = true;
        tenants.saveAndFlush(tenant);
        var xml = """
            <ENVELOPE><BODY><DATA><TALLYMESSAGE>
              <VOUCHER VCHTYPE="Sales" ACTION="Create">
                <DATE>20260401</DATE>
                <VOUCHERNUMBER>S-TALLY-GODOWN</VOUCHERNUMBER>
                <PARTYLEDGERNAME>Ravi Traders</PARTYLEDGERNAME>
                <ALLINVENTORYENTRIES.LIST>
                  <STOCKITEMNAME>Imported Tally Pen</STOCKITEMNAME>
                  <BILLEDQTY>2 PCS</BILLEDQTY>
                  <RATE>12/PCS</RATE>
                  <GODOWNNAME>Main Location</GODOWNNAME>
                </ALLINVENTORYENTRIES.LIST>
              </VOUCHER>
            </TALLYMESSAGE></DATA></BODY></ENVELOPE>
            """;
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "tally-sales-godown.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY_XML")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();

        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var errors = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(errors).contains("WAREHOUSE_WILL_BE_CREATED").doesNotContain("UNKNOWN_WAREHOUSE");

        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        var warehouse = warehouseRepository.findByTenantIdAndNameIgnoreCase(tenantId, "Main Location").orElseThrow();
        var movements = stockMovements.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
            .filter(movement -> movement.warehouseId.equals(warehouse.id) && movement.movementType == DomainEnums.MovementType.SALE)
            .toList();
        assertThat(movements).hasSize(1);
    }

    @Test
    void tallyXmlPurchaseVoucherCommitCreatesPurchaseInvoiceAndStockMovements() throws Exception {
        var registration = registerResponse("tally-purchase-commit-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var xml = """
            <ENVELOPE><BODY><DATA><TALLYMESSAGE>
              <VOUCHER VCHTYPE="Purchase" ACTION="Create">
                <DATE>20260402</DATE>
                <VOUCHERNUMBER>P-TALLY-COMMIT</VOUCHERNUMBER>
                <PARTYLEDGERNAME>ABC FMCG Supplier</PARTYLEDGERNAME>
                <ALLINVENTORYENTRIES.LIST>
                  <STOCKITEMNAME>Imported Tally Tea</STOCKITEMNAME>
                  <BILLEDQTY>5 PCS</BILLEDQTY>
                  <RATE>90/PCS</RATE>
                  <GODOWNNAME>Main Godown</GODOWNNAME>
                </ALLINVENTORYENTRIES.LIST>
                <ALLINVENTORYENTRIES.LIST>
                  <STOCKITEMNAME>Imported Tally Salt</STOCKITEMNAME>
                  <BILLEDQTY>7 PCS</BILLEDQTY>
                  <RATE>20/PCS</RATE>
                  <GODOWNNAME>Main Godown</GODOWNNAME>
                </ALLINVENTORYENTRIES.LIST>
              </VOUCHER>
            </TALLYMESSAGE></DATA></BODY></ENVELOPE>
            """;
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "tally-purchase.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();

        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        var invoices = purchaseInvoices.findByTenantId(tenantId).stream()
            .filter(invoice -> "P-TALLY-COMMIT".equals(invoice.invoiceNumber))
            .toList();
        assertThat(invoices).hasSize(1);
        assertThat(purchaseInvoiceItems.findByTenantIdAndPurchaseInvoiceId(tenantId, invoices.getFirst().id)).hasSize(2);
        var movements = stockMovements.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
            .filter(movement -> "PURCHASE_INVOICE".equals(movement.referenceType) && invoices.getFirst().id.equals(movement.referenceId))
            .toList();
        assertThat(movements).hasSize(2);

        var reconciliation = mvc.perform(get("/api/imports/" + batchId + "/reconciliation").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reconciliation).contains("purchaseInvoicesCreated").contains("stockMovementsCreated");
        assertThat(stock(token, movements.getFirst().productId.toString(), warehouse.get("id").asText())).isGreaterThan(0);
    }

    @Test
    void tallyPurchaseRatePoliciesCommitDerivedSignedAndZeroCostItems() throws Exception {
        var registration = registerResponse("tally-purchase-rates-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        postJson(token, "/api/products", """
            {"sku":"FREE-SCHEME","name":"Free Scheme Item","unitCode":"PCS","defaultPurchasePrice":7,"defaultSalesPrice":10,"reorderPoint":0}
            """);

        JsonNode upload;
        try (var input = getClass().getResourceAsStream("/sample-imports/tally-purchase-rate-cases.xml")) {
            assertThat(input).isNotNull();
            upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                    .file(new MockMultipartFile("file", "tally-purchase-rate-cases.xml", "application/xml", input))
                    .param("sourceType", "TALLY_XML")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        }
        var batchId = upload.get("batchId").asText();

        var preview = mvc.perform(get("/api/imports/" + batchId + "/preview").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview)
            .contains("DERIVED_FROM_AMOUNT")
            .contains("SIGN_NORMALIZED")
            .contains("ZERO_COST_ITEM")
            .contains("rawRate")
            .contains("parsedRate");

        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var validation = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(validation)
            .contains("PURCHASE_RATE_DERIVED_FROM_AMOUNT")
            .contains("PURCHASE_RATE_SIGN_NORMALIZED")
            .contains("ZERO_COST_PURCHASE_ITEM")
            .doesNotContain("INVALID_PURCHASE_RATE")
            .doesNotContain("INVALID_RATE");

        var commit = mapper.readTree(mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(commit.get("purchaseInvoicesCreated").asInt()).isEqualTo(1);
        assertThat(commit.get("purchaseInvoiceItemsCreated").asInt()).isEqualTo(4);
        assertThat(commit.get("voucherStockMovementsCreated").asInt()).isEqualTo(4);
        assertThat(commit.get("purchaseRatesDerivedFromAmount").asInt()).isEqualTo(1);
        assertThat(commit.get("zeroCostPurchaseItemsImported").asInt()).isEqualTo(1);
        assertThat(commit.get("purchaseRatesSignNormalized").asInt()).isEqualTo(1);
        assertThat(commit.get("ledgerLinesSkipped").asInt()).isEqualTo(2);

        var invoice = purchaseInvoices.findByTenantId(tenantId).stream()
            .filter(candidate -> "P-RATE-CASES".equals(candidate.invoiceNumber))
            .findFirst().orElseThrow();
        var items = purchaseInvoiceItems.findByTenantIdAndPurchaseInvoiceId(tenantId, invoice.id);
        assertThat(items).hasSize(4);
        assertThat(items).extracting(item -> item.rate)
            .containsExactlyInAnyOrder(new BigDecimal("30.00"), new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("0.00"));
        assertThat(invoice.totalAmount).isEqualByComparingTo("1000.00");

        var freeProduct = productRepository.findByTenantIdAndNameIgnoreCase(tenantId, "Free Scheme Item").orElseThrow();
        assertThat(freeProduct.defaultPurchasePrice).isEqualByComparingTo("7.00");
        assertThat(stock(token, freeProduct.id.toString(), warehouse.get("id").asText())).isEqualTo(5);

        var reconciliation = mvc.perform(get("/api/imports/" + batchId + "/reconciliation").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reconciliation)
            .contains("\"purchaseRatesDerivedFromAmount\":1")
            .contains("\"zeroCostPurchaseItemsImported\":1")
            .contains("\"purchaseRatesSignNormalized\":1")
            .contains("\"ledgerLinesSkipped\":2");
    }

    @Test
    void tallyPurchaseInvalidRateProducesOneSpecificBlockingError() throws Exception {
        var token = register("tally-invalid-purchase-rate-" + System.nanoTime() + "@example.com");
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var xml = """
            <ENVELOPE><VOUCHER VCHTYPE="Purchase" ACTION="Create">
              <DATE>20260601</DATE><VOUCHERNUMBER>P-BAD-RATE</VOUCHERNUMBER><PARTYLEDGERNAME>Sample Supplier</PARTYLEDGERNAME>
              <ALLINVENTORYENTRIES.LIST>
                <STOCKITEMNAME>Invalid Rate Item</STOCKITEMNAME><BILLEDQTY>10 Nos</BILLEDQTY>
                <RATE>not-a-rate</RATE><AMOUNT>not-an-amount</AMOUNT><GODOWNNAME>Main Godown</GODOWNNAME>
              </ALLINVENTORYENTRIES.LIST>
            </VOUCHER></ENVELOPE>
            """;
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "invalid-purchase-rate.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY_XML")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();

        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var issues = mapper.readTree(mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var invalidRateCount = 0;
        for (var issue : issues) {
            if ("INVALID_PURCHASE_RATE".equals(issue.get("errorCode").asText())) invalidRateCount++;
        }
        assertThat(invalidRateCount).isEqualTo(1);
    }

    @Test
    void tallyDebitNoteUsesReturnOutWithoutMisleadingRateError() throws Exception {
        var token = register("tally-purchase-return-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"RETURN-ITEM","name":"Returned Tally Item","unitCode":"PCS","defaultPurchasePrice":5,"defaultSalesPrice":8,"reorderPoint":0}
            """);
        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-RETURN-BASE","invoiceDate":"2026-06-01","items":[{"productId":"%s","quantity":10,"rate":5}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));
        var xml = """
            <ENVELOPE><VOUCHER VCHTYPE="Debit Note" ACTION="Create">
              <DATE>20260602</DATE><VOUCHERNUMBER>DN-1</VOUCHERNUMBER><PARTYLEDGERNAME>Sample Supplier</PARTYLEDGERNAME>
              <ALLINVENTORYENTRIES.LIST>
                <STOCKITEMNAME>Returned Tally Item</STOCKITEMNAME><BILLEDQTY>-2 PCS</BILLEDQTY>
                <RATE>-5/PCS</RATE><AMOUNT>10</AMOUNT><GODOWNNAME>Main Godown</GODOWNNAME>
              </ALLINVENTORYENTRIES.LIST>
            </VOUCHER></ENVELOPE>
            """;
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "purchase-return.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY_XML")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();

        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var validation = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(validation)
            .contains("PURCHASE_RETURN_REQUIRES_REVIEW")
            .contains("PURCHASE_RATE_SIGN_NORMALIZED")
            .doesNotContain("INVALID_PURCHASE_RATE")
            .doesNotContain("Rate must be positive");

        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        assertThat(stock(token, product.get("id").asText(), warehouse.get("id").asText())).isEqualTo(8);
    }

    @Test
    void cancelledTallyVoucherIsWarningMarkedAndSkippedOnCommit() throws Exception {
        var registration = registerResponse("tally-cancelled-" + System.nanoTime() + "@example.com");
        var token = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        postJson(token, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var xml = """
            <ENVELOPE><BODY><DATA><TALLYMESSAGE>
              <VOUCHER VCHTYPE="Purchase" ACTION="Delete">
                <DATE>20260403</DATE>
                <VOUCHERNUMBER>P-TALLY-CANCELLED</VOUCHERNUMBER>
                <ISCANCELLED>Yes</ISCANCELLED>
                <PARTYLEDGERNAME>ABC FMCG Supplier</PARTYLEDGERNAME>
                <ALLINVENTORYENTRIES.LIST>
                  <STOCKITEMNAME>Cancelled Tally Item</STOCKITEMNAME>
                  <BILLEDQTY>5 PCS</BILLEDQTY>
                  <RATE>90/PCS</RATE>
                  <GODOWNNAME>Main Godown</GODOWNNAME>
                </ALLINVENTORYENTRIES.LIST>
              </VOUCHER>
            </TALLYMESSAGE></DATA></BODY></ENVELOPE>
            """;
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "tally-cancelled.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();
        mvc.perform(post("/api/imports/" + batchId + "/validate").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        var errors = mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(errors).contains("CANCELLED_VOUCHER_SKIPPED").contains("WARNING");

        mvc.perform(post("/api/imports/" + batchId + "/commit").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        assertThat(purchaseInvoices.findByTenantId(tenantId).stream().noneMatch(invoice -> "P-TALLY-CANCELLED".equals(invoice.invoiceNumber))).isTrue();
        assertThat(stockMovements.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent().stream().noneMatch(movement -> UUID.fromString(batchId).equals(movement.referenceId))).isTrue();
        var reconciliation = mvc.perform(get("/api/imports/" + batchId + "/reconciliation").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reconciliation).contains("\"stockMovementsCreated\":0");
    }

    @Test
    void importBatchAndErrorsAreTenantScoped() throws Exception {
        var tokenA = register("import-scope-a-" + System.nanoTime() + "@example.com");
        var tokenB = register("import-scope-b-" + System.nanoTime() + "@example.com");
        postJson(tokenB, "/api/warehouses", "{\"name\":\"Main Godown\",\"code\":\"MAIN\"}");
        var csv = "Item Name,Unit,Opening Stock,Purchase Price,Godown\nTenant B Import Product,PCS,5,8,Main Godown\n";
        var file = new MockMultipartFile("file", "tenant-b-products.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(file)
                .param("sourceType", "CSV")
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var batchId = upload.get("batchId").asText();

        mvc.perform(get("/api/imports/" + batchId).header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/imports/" + batchId + "/errors").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/imports/" + batchId + "/reconciliation").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
    }

    @Test
    void portalEndpointsRequireCustomerUserAndUseLinkedCustomer() throws Exception {
        var registration = registerResponse("portal-owner-" + System.nanoTime() + "@example.com");
        var ownerToken = registration.get("accessToken").asText();
        var tenantId = java.util.UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(ownerToken, "/api/warehouses", "{\"name\":\"Portal Warehouse\",\"code\":\"PORTAL\"}");
        var product = postJson(ownerToken, "/api/products", """
            {"sku":"SKU-PORTAL","name":"Portal Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":20,"reorderPoint":2}
            """);
        postJson(ownerToken, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-PORTAL","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":10,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));
        var customer = postJson(ownerToken, "/api/customers", "{\"name\":\"Portal Customer\"}");

        mvc.perform(get("/api/portal/products").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isForbidden());

        assignPortalProduct(tenantId, customer.get("id").asText(), product.get("id").asText(), "19.50");
        var portalUser = new UserAccount();
        portalUser.email = "portal-" + System.nanoTime() + "@example.com";
        portalUser.fullName = "Portal User";
        portalUser.passwordHash = passwordEncoder.encode("password123");
        users.save(portalUser);
        var membership = new UserTenantMembership();
        membership.tenantId = tenantId;
        membership.userId = portalUser.id;
        membership.customerId = java.util.UUID.fromString(customer.get("id").asText());
        membership.role = DomainEnums.Role.CUSTOMER_USER;
        memberships.save(membership);
        var customerToken = jwtService.issue(portalUser.id, tenantId, portalUser.email, DomainEnums.Role.CUSTOMER_USER);

        mvc.perform(get("/api/portal/products").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isOk());
        mvc.perform(post("/api/portal/orders")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"productId":"%s","quantity":2}]}
                    """.formatted(product.get("id").asText())))
            .andExpect(status().isOk());
    }

    @Test
    void customerPortalIsLimitedToLinkedCustomerAndCannotAccessInternalApis() throws Exception {
        var registration = registerResponse("portal-scope-owner-" + System.nanoTime() + "@example.com");
        var ownerToken = registration.get("accessToken").asText();
        var tenantId = java.util.UUID.fromString(registration.get("tenantId").asText());
        var warehouse = postJson(ownerToken, "/api/warehouses", "{\"name\":\"Portal Scope Warehouse\",\"code\":\"PSW\"}");
        var product = postJson(ownerToken, "/api/products", """
            {"sku":"SKU-PORTAL-SCOPE","name":"Portal Scope Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":20,"reorderPoint":2}
            """);
        postJson(ownerToken, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-PORTAL-SCOPE","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":20,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));
        var linkedCustomer = postJson(ownerToken, "/api/customers", "{\"name\":\"Linked Portal Customer\"}");
        var otherCustomer = postJson(ownerToken, "/api/customers", "{\"name\":\"Other Portal Customer\"}");
        postJson(ownerToken, "/api/sales", """
            {"customerId":"%s","warehouseId":"%s","invoiceNumber":"S-LINKED-PORTAL","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":1,"rate":20}]}
            """.formatted(linkedCustomer.get("id").asText(), warehouse.get("id").asText(), product.get("id").asText()));
        postJson(ownerToken, "/api/sales", """
            {"customerId":"%s","warehouseId":"%s","invoiceNumber":"S-OTHER-PORTAL","invoiceDate":"2026-01-03","items":[{"productId":"%s","quantity":1,"rate":20}]}
            """.formatted(otherCustomer.get("id").asText(), warehouse.get("id").asText(), product.get("id").asText()));
        assignPortalProduct(tenantId, linkedCustomer.get("id").asText(), product.get("id").asText(), "20.00");

        var otherOrder = new SalesOrder();
        otherOrder.tenantId = tenantId;
        otherOrder.customerId = UUID.fromString(otherCustomer.get("id").asText());
        otherOrder.orderNumber = "SO-OTHER-PORTAL";
        otherOrder.status = "OPEN";
        salesOrders.save(otherOrder);

        var portalUser = new UserAccount();
        portalUser.email = "portal-scope-" + System.nanoTime() + "@example.com";
        portalUser.fullName = "Portal Scope User";
        portalUser.passwordHash = passwordEncoder.encode("password123");
        users.save(portalUser);
        var membership = new UserTenantMembership();
        membership.tenantId = tenantId;
        membership.userId = portalUser.id;
        membership.customerId = UUID.fromString(linkedCustomer.get("id").asText());
        membership.role = DomainEnums.Role.CUSTOMER_USER;
        memberships.save(membership);
        var customerToken = jwtService.issue(portalUser.id, tenantId, portalUser.email, DomainEnums.Role.CUSTOMER_USER);

        postJson(customerToken, "/api/portal/orders", """
            {"items":[{"productId":"%s","quantity":2}]}
            """.formatted(product.get("id").asText()));

        var invoices = mvc.perform(get("/api/portal/invoices").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(invoices).contains("S-LINKED-PORTAL").doesNotContain("S-OTHER-PORTAL");

        var orders = mvc.perform(get("/api/portal/orders").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(orders).contains("PORTAL-SO-").doesNotContain("SO-OTHER-PORTAL");

        mvc.perform(get("/api/reports/export/current-stock").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/imports").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void customerPortalDoesNotShowAllActiveProductsByDefault() throws Exception {
        var registration = registerResponse("portal-default-hidden-" + System.nanoTime() + "@example.com");
        var ownerToken = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var product = postJson(ownerToken, "/api/products", """
            {"sku":"SKU-HIDDEN-PORTAL","name":"Hidden Portal Product","unitCode":"PCS","defaultPurchasePrice":12,"defaultSalesPrice":24,"reorderPoint":2}
            """);
        var customer = postJson(ownerToken, "/api/customers", "{\"name\":\"Default Hidden Customer\"}");
        var customerToken = customerPortalToken(tenantId, customer.get("id").asText(), "portal-default-hidden");

        var products = mvc.perform(get("/api/portal/products").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(products).doesNotContain("Hidden Portal Product").isEqualTo("[]");

        mvc.perform(post("/api/portal/orders")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"productId":"%s","quantity":1}]}
                    """.formatted(product.get("id").asText())))
            .andExpect(status().isNotFound());
    }

    @Test
    void customerPortalShowsOnlyAssignedPriceListProductsAndNoPurchaseCost() throws Exception {
        var registration = registerResponse("portal-assigned-" + System.nanoTime() + "@example.com");
        var ownerToken = registration.get("accessToken").asText();
        var tenantId = UUID.fromString(registration.get("tenantId").asText());
        var assigned = postJson(ownerToken, "/api/products", """
            {"sku":"SKU-ASSIGNED-PORTAL","name":"Assigned Portal Product","unitCode":"PCS","defaultPurchasePrice":11.25,"defaultSalesPrice":25,"reorderPoint":2}
            """);
        var unassigned = postJson(ownerToken, "/api/products", """
            {"sku":"SKU-UNASSIGNED-PORTAL","name":"Unassigned Portal Product","unitCode":"PCS","defaultPurchasePrice":13.50,"defaultSalesPrice":30,"reorderPoint":2}
            """);
        var customer = postJson(ownerToken, "/api/customers", "{\"name\":\"Assigned Catalog Customer\"}");
        assignPortalProduct(tenantId, customer.get("id").asText(), assigned.get("id").asText(), "22.75");
        var customerToken = customerPortalToken(tenantId, customer.get("id").asText(), "portal-assigned");

        var tokenB = register("portal-other-tenant-" + System.nanoTime() + "@example.com");
        postJson(tokenB, "/api/products", """
            {"sku":"SKU-OTHER-TENANT-PORTAL","name":"Other Tenant Portal Product","unitCode":"PCS","defaultPurchasePrice":99,"defaultSalesPrice":199,"reorderPoint":2}
            """);

        var products = mvc.perform(get("/api/portal/products").header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(products)
            .contains("Assigned Portal Product")
            .contains("22.75")
            .doesNotContain("Unassigned Portal Product")
            .doesNotContain(unassigned.get("id").asText())
            .doesNotContain("Other Tenant Portal Product")
            .doesNotContain("defaultPurchasePrice")
            .doesNotContain("11.25");

        mvc.perform(post("/api/portal/orders")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[{"productId":"%s","quantity":1}]}
                    """.formatted(unassigned.get("id").asText())))
            .andExpect(status().isNotFound());
    }

    @Test
    void deadStockAndProfitInsightExposeDataBackedEvidence() throws Exception {
        var token = register("insights-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Insight Warehouse\",\"code\":\"INS\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-INSIGHT","name":"Insight Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":20,"reorderPoint":2}
            """);
        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-INSIGHT","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":10,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));

        var deadStock = mapper.readTree(mvc.perform(get("/api/dashboard/dead-stock").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(deadStock.toString()).contains("Insight Product");

        var profit = mapper.readTree(mvc.perform(get("/api/insights/profit-drop").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(profit.get("evidence").has("currentMonthRevenue")).isTrue();
        assertThat(profit.get("topReasons").isArray()).isTrue();
    }

    @Test
    void auditLogsReportsAndLowStockAliasAreTenantScoped() throws Exception {
        var tokenA = register("report-a-" + System.nanoTime() + "@example.com");
        var warehouseA = postJson(tokenA, "/api/warehouses", "{\"name\":\"Report Warehouse\",\"code\":\"RPT\"}");
        var productA = postJson(tokenA, "/api/products", """
            {"sku":"SKU-REPORT-A","name":"Report Tenant Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":20,"reorderPoint":5}
            """);
        postJson(tokenA, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-REPORT-A","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":3,"rate":10}]}
            """.formatted(warehouseA.get("id").asText(), productA.get("id").asText()));

        var lowStock = mapper.readTree(mvc.perform(get("/api/alerts/low-stock").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(lowStock.toString()).contains("Report Tenant Product");

        var auditLogs = mapper.readTree(mvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(auditLogs.toString()).contains("STOCK_MOVEMENT_CREATED");

        var csvA = mvc.perform(get("/api/reports/export/current-stock").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csvA).contains("Report Tenant Product");

        var tokenB = register("report-b-" + System.nanoTime() + "@example.com");
        var csvB = mvc.perform(get("/api/reports/export/current-stock").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csvB).doesNotContain("Report Tenant Product");
    }

    @Test
    void reportExportAndAuditLogsDoNotIncludeOtherTenantData() throws Exception {
        var tokenA = register("isolation-report-a-" + System.nanoTime() + "@example.com");

        var tokenB = register("isolation-report-b-" + System.nanoTime() + "@example.com");
        var warehouseB = postJson(tokenB, "/api/warehouses", "{\"name\":\"Tenant B Warehouse\",\"code\":\"TBW\"}");
        var productB = postJson(tokenB, "/api/products", """
            {"sku":"SKU-SECRET-B","name":"Secret Tenant B Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":20,"reorderPoint":2}
            """);
        postJson(tokenB, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-SECRET-B","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":3,"rate":10}]}
            """.formatted(warehouseB.get("id").asText(), productB.get("id").asText()));

        var csvA = mvc.perform(get("/api/reports/export/current-stock").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csvA).doesNotContain("Secret Tenant B Product");

        var auditA = mvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(auditA).doesNotContain(productB.get("id").asText()).doesNotContain("P-SECRET-B");
    }

    @Test
    void aiAssistantUsesOnlyCurrentTenantData() throws Exception {
        var tokenA = register("ai-tenant-a-" + System.nanoTime() + "@example.com");
        var warehouseA = postJson(tokenA, "/api/warehouses", "{\"name\":\"AI Tenant A Warehouse\",\"code\":\"AITWA\"}");
        var productA = postJson(tokenA, "/api/products", """
            {"sku":"SKU-AI-SECRET-A","name":"Secret AI Tenant A Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":20,"reorderPoint":2}
            """);
        postJson(tokenA, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-AI-SECRET-A","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":3,"rate":10}]}
            """.formatted(warehouseA.get("id").asText(), productA.get("id").asText()));

        var tokenB = register("ai-tenant-b-" + System.nanoTime() + "@example.com");
        var response = mvc.perform(post("/api/ai/assistant/chat")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Which products are dead stock?\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("Secret AI Tenant A Product").doesNotContain(productA.get("id").asText());
    }

    private String register(String email) throws Exception {
        return registerResponse(email).get("accessToken").asText();
    }

    private JsonNode registerResponse(String email) throws Exception {
        var response = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Tenant","businessMode":"HYBRID","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private JsonNode postJson(String token, String path, String body) throws Exception {
        var response = mvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assignPortalProduct(UUID tenantId, String customerId, String productId, String price) {
        var row = new CustomerPriceList();
        row.tenantId = tenantId;
        row.customerId = UUID.fromString(customerId);
        row.productId = UUID.fromString(productId);
        row.price = new BigDecimal(price);
        priceLists.save(row);
    }

    private String uploadTallyDisplayStockXml(String token, String xml) throws Exception {
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", "tally-display-stock.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY_XML")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return upload.get("batchId").asText();
    }

    private String uploadTallyVoucherXml(String token, String fileName, String xml) throws Exception {
        var upload = mapper.readTree(mvc.perform(multipart("/api/imports/upload")
                .file(new MockMultipartFile("file", fileName, "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("sourceType", "TALLY_XML")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return upload.get("batchId").asText();
    }

    private String tallyVoucherXml(String voucherType, String voucherNumber, String date, String party, String product, String quantity, String rate) {
        return "<ENVELOPE>" + tallyVoucherNode(voucherType, voucherNumber, date, party, product, quantity, rate) + "</ENVELOPE>";
    }

    private String tallyVoucherNode(String voucherType, String voucherNumber, String date, String party, String product, String quantity, String rate) {
        return """
            <VOUCHER VCHTYPE="%s" ACTION="Create">
              <DATE>%s</DATE><VOUCHERNUMBER>%s</VOUCHERNUMBER><PARTYLEDGERNAME>%s</PARTYLEDGERNAME>
              <ALLINVENTORYENTRIES.LIST>
                <STOCKITEMNAME>%s</STOCKITEMNAME><BILLEDQTY>%s</BILLEDQTY><RATE>%s</RATE><GODOWNNAME>Main Godown</GODOWNNAME>
              </ALLINVENTORYENTRIES.LIST>
            </VOUCHER>
            """.formatted(voucherType, date, voucherNumber, party, product, quantity, rate);
    }

    private String tallyStockSnapshotXml(String productName, String closingQuantity, String rate) {
        return """
            <ENVELOPE>
              <DSPACCNAME><DSPDISPNAME>%s</DSPDISPNAME></DSPACCNAME>
              <DSPSTKINFO><DSPSTKCL>
                <DSPCLQTY>%s</DSPCLQTY>
                <DSPCLRATE>%s</DSPCLRATE>
                <DSPCLAMTA>0</DSPCLAMTA>
              </DSPSTKCL></DSPSTKINFO>
            </ENVELOPE>
            """.formatted(productName, closingQuantity, rate);
    }

    private String customerPortalToken(UUID tenantId, String customerId, String emailPrefix) {
        var portalUser = new UserAccount();
        portalUser.email = emailPrefix + "-" + System.nanoTime() + "@example.com";
        portalUser.fullName = "Portal Customer User";
        portalUser.passwordHash = passwordEncoder.encode("password123");
        users.save(portalUser);
        var membership = new UserTenantMembership();
        membership.tenantId = tenantId;
        membership.userId = portalUser.id;
        membership.customerId = UUID.fromString(customerId);
        membership.role = DomainEnums.Role.CUSTOMER_USER;
        memberships.save(membership);
        return jwtService.issue(portalUser.id, tenantId, portalUser.email, DomainEnums.Role.CUSTOMER_USER);
    }

    private void setAllowNegativeStock(UUID tenantId, boolean allowed) {
        var tenant = tenants.findById(tenantId).orElseThrow();
        tenant.allowNegativeStock = allowed;
        tenants.saveAndFlush(tenant);
    }

    private int concurrentSaleStatus(CountDownLatch start, String token, String warehouseId, String productId, String invoiceNumber) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/sales")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"warehouseId":"%s","invoiceNumber":"%s","invoiceDate":"2026-01-02","items":[{"productId":"%s","quantity":4,"rate":15}]}
                    """.formatted(warehouseId, invoiceNumber, productId)))
            .andReturn().getResponse().getStatus();
    }

    private int stock(String token, String productId, String warehouseId) throws Exception {
        var response = mapper.readTree(mvc.perform(get("/api/stock/current")
                .param("productId", productId)
                .param("warehouseId", warehouseId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return response.get(0).get("currentStock").asInt();
    }
}
