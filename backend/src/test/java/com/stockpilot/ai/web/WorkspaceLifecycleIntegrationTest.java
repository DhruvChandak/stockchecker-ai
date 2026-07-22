package com.stockpilot.ai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.config.JwtService;
import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.domain.ImportBatch;
import com.stockpilot.ai.domain.ImportFile;
import com.stockpilot.ai.repo.Repositories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Repositories.TenantRepository tenants;
    @Autowired Repositories.UserRepository users;
    @Autowired Repositories.MembershipRepository memberships;
    @Autowired Repositories.ProductRepository products;
    @Autowired Repositories.StockMovementRepository stockMovements;
    @Autowired Repositories.PurchaseInvoiceRepository purchaseInvoices;
    @Autowired Repositories.ImportBatchRepository importBatches;
    @Autowired Repositories.ImportFileRepository importFiles;
    @Autowired Repositories.AuditLogRepository auditLogs;
    @Autowired JwtService jwtService;

    @Test
    void deleteImpactIsTenantScopedAndResetKeepsIdentityButClearsBusinessData() throws Exception {
        var owner = register("reset-owner-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(owner.token, "/api/warehouses", "{\"name\":\"Reset Warehouse\",\"code\":\"RST\"}");
        var product = postJson(owner.token, "/api/products", """
            {"sku":"RESET-1","name":"Reset Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        postJson(owner.token, "/api/customers", "{\"name\":\"Reset Customer\"}");
        postJson(owner.token, "/api/suppliers", "{\"name\":\"Reset Supplier\"}");
        postJson(owner.token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"RESET-P-1","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":4,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));
        createImportMetadata(owner);

        var other = register("reset-other-" + System.nanoTime() + "@example.com");
        var otherProduct = postJson(other.token, "/api/products", """
            {"sku":"KEEP-1","name":"Other Tenant Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);

        var impact = getJson(owner.token, "/api/tenants/current/delete-impact");
        assertThat(impact.get("products").asLong()).isEqualTo(1);
        assertThat(impact.get("stockMovements").asLong()).isEqualTo(1);
        assertThat(impact.get("purchaseInvoices").asLong()).isEqualTo(1);
        assertThat(impact.get("importBatches").asLong()).isEqualTo(1);
        assertThat(impact.get("uploadedFiles").asLong()).isEqualTo(1);

        mvc.perform(post("/api/tenants/current/reset-business-data")
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"RESET WORKSPACE DATA\"}"))
            .andExpect(status().isOk());

        assertThat(products.findByTenantIdAndId(owner.tenantId, UUID.fromString(product.get("id").asText()))).isEmpty();
        assertThat(stockMovements.findByTenantId(owner.tenantId, org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isZero();
        assertThat(purchaseInvoices.findByTenantId(owner.tenantId)).isEmpty();
        assertThat(importBatches.findByTenantIdAndId(owner.tenantId, UUID.fromString(impactImportId(owner)))).isEmpty();
        assertThat(users.findById(owner.userId)).isPresent();
        assertThat(tenants.findByIdAndStatus(owner.tenantId, DomainEnums.TenantStatus.ACTIVE)).isPresent();
        assertThat(memberships.findByTenantIdAndUserId(owner.tenantId, owner.userId)).isPresent();
        assertThat(products.findByTenantIdAndId(other.tenantId, UUID.fromString(otherProduct.get("id").asText()))).isPresent();
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(owner.tenantId)).extracting(log -> log.action).contains("WORKSPACE_DATA_RESET");

        var dashboard = getJson(owner.token, "/api/dashboard/summary");
        assertThat(dashboard.get("totalStockValue").decimalValue()).isZero();
        assertThat(dashboard.get("monthlySales").decimalValue()).isZero();
    }

    @Test
    void resetRequiresOwnerAndCustomerUserCannotViewImpact() throws Exception {
        var session = register("reset-role-" + System.nanoTime() + "@example.com");
        var membership = memberships.findByTenantIdAndUserId(session.tenantId, session.userId).orElseThrow();
        membership.role = DomainEnums.Role.CUSTOMER_USER;
        memberships.save(membership);
        var customerToken = jwtService.issue(session.userId, session.tenantId, session.email, DomainEnums.Role.CUSTOMER_USER);

        mvc.perform(get("/api/tenants/current/delete-impact").header("Authorization", bearer(customerToken)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/tenants/current/reset-business-data")
                .header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"RESET WORKSPACE DATA\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidResetConfirmationLeavesWorkspaceUntouched() throws Exception {
        var owner = register("reset-confirm-" + System.nanoTime() + "@example.com");
        var product = postJson(owner.token, "/api/products", """
            {"sku":"SAFE-1","name":"Keep Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);

        mvc.perform(post("/api/tenants/current/reset-business-data")
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"WRONG\"}"))
            .andExpect(status().isBadRequest());

        assertThat(products.findByTenantIdAndId(owner.tenantId, UUID.fromString(product.get("id").asText()))).isPresent();
    }

    @Test
    void deletingOnlyWorkspaceInvalidatesOldTokenAndSupportsWorkspaceOnboarding() throws Exception {
        var owner = register("workspace-delete-" + System.nanoTime() + "@example.com");

        var response = mapper.readTree(mvc.perform(delete("/api/tenants/current")
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE WORKSPACE\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        assertThat(response.get("onboardingRequired").asBoolean()).isTrue();
        assertThat(response.get("nextTenantId").isNull()).isTrue();
        assertThat(tenants.findById(owner.tenantId).orElseThrow().status).isEqualTo(DomainEnums.TenantStatus.DELETED);
        assertThat(memberships.findByTenantIdAndUserId(owner.tenantId, owner.userId)).isEmpty();
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(owner.tenantId)).extracting(log -> log.action).contains("WORKSPACE_DELETED");

        mvc.perform(get("/api/tenants/current").header("Authorization", bearer(owner.token)))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tenants/current").header("Authorization", bearer(response.get("accessToken").asText())))
            .andExpect(status().isForbidden());

        var newWorkspace = mapper.readTree(mvc.perform(post("/api/tenants")
                .header("Authorization", bearer(response.get("accessToken").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\":\"Fresh Workspace\",\"businessMode\":\"RETAIL\",\"currency\":\"INR\",\"gstEnabled\":true}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/tenants/current").header("Authorization", bearer(newWorkspace.get("accessToken").asText())))
            .andExpect(status().isOk());
    }

    @Test
    void workspaceLessUserCanDeleteAccountAfterDeletingLastWorkspace() throws Exception {
        var owner = register("workspace-account-delete-" + System.nanoTime() + "@example.com");
        var workspaceDeletion = mapper.readTree(mvc.perform(delete("/api/tenants/current")
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE WORKSPACE\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        mvc.perform(delete("/api/account")
                .header("Authorization", bearer(workspaceDeletion.get("accessToken").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE MY ACCOUNT\"}"))
            .andExpect(status().isOk());
        assertThat(users.findById(owner.userId).orElseThrow().active).isFalse();
    }

    @Test
    void workspaceDeletionRequiresOwner() throws Exception {
        var session = register("workspace-role-" + System.nanoTime() + "@example.com");
        var membership = memberships.findByTenantIdAndUserId(session.tenantId, session.userId).orElseThrow();
        membership.role = DomainEnums.Role.ADMIN;
        memberships.save(membership);
        var adminToken = jwtService.issue(session.userId, session.tenantId, session.email, DomainEnums.Role.ADMIN);

        mvc.perform(delete("/api/tenants/current")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE WORKSPACE\"}"))
            .andExpect(status().isForbidden());
        assertThat(tenants.findByIdAndStatus(session.tenantId, DomainEnums.TenantStatus.ACTIVE)).isPresent();
    }

    @Test
    void accountDeletionIsBlockedForOwner() throws Exception {
        var owner = register("account-owner-" + System.nanoTime() + "@example.com");
        mvc.perform(delete("/api/account")
                .header("Authorization", bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE MY ACCOUNT\"}"))
            .andExpect(status().isConflict());
        assertThat(users.findById(owner.userId).orElseThrow().active).isTrue();
    }

    @Test
    void accountDeletionAnonymizesNonOwnerAndInvalidatesToken() throws Exception {
        var session = register("account-admin-" + System.nanoTime() + "@example.com");
        var membership = memberships.findByTenantIdAndUserId(session.tenantId, session.userId).orElseThrow();
        membership.role = DomainEnums.Role.ADMIN;
        memberships.save(membership);
        var adminToken = jwtService.issue(session.userId, session.tenantId, session.email, DomainEnums.Role.ADMIN);

        mvc.perform(delete("/api/account")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE MY ACCOUNT\"}"))
            .andExpect(status().isOk());

        var deleted = users.findById(session.userId).orElseThrow();
        assertThat(deleted.active).isFalse();
        assertThat(deleted.email).startsWith("deleted+").endsWith("@deleted.local");
        assertThat(memberships.findByUserId(session.userId)).isEmpty();
        assertThat(auditLogs.findByTenantIdOrderByCreatedAtDesc(session.tenantId)).extracting(log -> log.action).contains("ACCOUNT_DEACTIVATED");
        mvc.perform(get("/api/tenants/current").header("Authorization", bearer(adminToken)))
            .andExpect(status().isUnauthorized());
    }

    private Session register(String email) throws Exception {
        var response = mapper.readTree(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Lifecycle Tenant","businessMode":"HYBRID","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        return new Session(response.get("accessToken").asText(), UUID.fromString(response.get("tenantId").asText()), UUID.fromString(response.get("userId").asText()), email);
    }

    private JsonNode postJson(String token, String path, String body) throws Exception {
        return mapper.readTree(mvc.perform(post(path)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(String token, String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private void createImportMetadata(Session owner) {
        var batch = new ImportBatch();
        batch.tenantId = owner.tenantId;
        batch.sourceType = DomainEnums.SourceType.TALLY_XML;
        batch.status = DomainEnums.ImportStatus.UPLOADED;
        batch.originalFileName = "reset-test.xml";
        importBatches.save(batch);
        var file = new ImportFile();
        file.tenantId = owner.tenantId;
        file.importBatchId = batch.id;
        file.fileName = "reset-test.xml";
        file.storageKey = owner.tenantId + "/reset-test.xml";
        file.sizeBytes = 10;
        importFiles.save(file);
        owner.importBatchId = batch.id;
    }

    private String impactImportId(Session owner) {
        return owner.importBatchId.toString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static final class Session {
        final String token;
        final UUID tenantId;
        final UUID userId;
        final String email;
        UUID importBatchId;

        private Session(String token, UUID tenantId, UUID userId, String email) {
            this.token = token;
            this.tenantId = tenantId;
            this.userId = userId;
            this.email = email;
        }
    }
}
