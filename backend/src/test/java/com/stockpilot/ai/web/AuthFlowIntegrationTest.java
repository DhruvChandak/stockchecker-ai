package com.stockpilot.ai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.repo.Repositories;
import com.stockpilot.ai.service.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext applicationContext;
    @Autowired Repositories.UserRepository users;

    @Test
    void registerLoginAndMeWork() throws Exception {
        var email = "owner-" + System.nanoTime() + "@example.com";
        var register = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Test Shop","businessMode":"RETAIL","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(register).get("accessToken").asText()).isNotBlank();

        var login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var token = mapper.readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void loginActivatesExistingUnverifiedAccountWhenVerificationIsDisabled() throws Exception {
        var email = "local-unverified-" + System.nanoTime() + "@example.com";
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Local Shop","businessMode":"RETAIL","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk());

        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.emailVerified = false;
        user.emailVerifiedAt = null;
        users.save(user);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password123"}
                    """.formatted(email)))
            .andExpect(status().isOk());

        var activated = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(activated.emailVerified).isTrue();
        assertThat(activated.emailVerifiedAt).isNotNull();
    }

    @Test
    void demoSeederIsDisabledAndNewWorkspaceStartsEmpty() throws Exception {
        assertThat(applicationContext.getBeansOfType(DemoDataSeeder.class)).isEmpty();

        var email = "clean-workspace-" + System.nanoTime() + "@example.com";
        var register = mapper.readTree(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Clean Tally Workspace","businessMode":"HYBRID","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        var token = register.get("accessToken").asText();
        var tenantId = register.get("tenantId").asText();

        var tenant = mapper.readTree(mvc.perform(get("/api/tenants/current")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(tenant.get("id").asText()).isEqualTo(tenantId);
        assertThat(tenant.get("name").asText()).isEqualTo("Clean Tally Workspace");

        var products = mapper.readTree(mvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        var customers = mapper.readTree(mvc.perform(get("/api/customers")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        var suppliers = mapper.readTree(mvc.perform(get("/api/suppliers")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        assertThat(products.get("content")).isEmpty();
        assertThat(customers.get("content")).isEmpty();
        assertThat(suppliers.get("content")).isEmpty();
    }

    @Test
    void cleanWorkspaceReturnsEmptyIntelligenceAndNoDemoValues() throws Exception {
        var token = registerToken("clean-intelligence-" + System.nanoTime() + "@example.com");

        var summary = mapper.readTree(getJson(token, "/api/dashboard/summary"));
        assertThat(summary.get("totalStockValue").asInt()).isZero();
        assertThat(summary.get("monthlySales").asInt()).isZero();
        assertThat(summary.get("grossProfit").asInt()).isZero();
        assertThat(summary.get("lowStockCount").asInt()).isZero();
        assertThat(summary.get("deadStockValue").asInt()).isZero();
        assertThat(summary.get("outstandingReceivables").asInt()).isZero();
        assertThat(summary.get("totalProducts").asInt()).isZero();
        assertThat(summary.get("totalCustomers").asInt()).isZero();
        assertThat(summary.get("totalSuppliers").asInt()).isZero();

        assertThat(mapper.readTree(getJson(token, "/api/dashboard/low-stock"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/dashboard/dead-stock"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/dashboard/actions"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/dashboard/top-products"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/dashboard/slow-moving-products"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/forecast/results"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/reorder/suggestions"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/dead-stock"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/data-quality/products/duplicates"))).isEmpty();
        assertThat(mapper.readTree(getJson(token, "/api/data-quality/products/missing-fields"))).isEmpty();

        var dataQuality = mapper.readTree(getJson(token, "/api/data-quality/summary"));
        assertThat(dataQuality.get("totalProducts").asInt()).isZero();
        assertThat(dataQuality.get("duplicateGroups").asInt()).isZero();
        assertThat(dataQuality.get("productsWithMissingFields").asInt()).isZero();
        assertThat(dataQuality.get("qualityScore").asInt()).isEqualTo(100);

        var imports = mapper.readTree(getJson(token, "/api/imports?size=20"));
        assertThat(imports.get("content")).isEmpty();

        var ai = mapper.readTree(postJson(token, "/api/ai/assistant/chat", "{\"message\":\"Why did my profit drop this month?\"}"));
        assertThat(ai.get("response").asText()).isEqualTo("No business data is available yet. Import Tally/Excel data or add products, purchases, and sales first.");
        assertThat(ai.toString()).doesNotContain("Parle-G").doesNotContain("Surf Excel").doesNotContain("MAGGI").doesNotContain("demo-tally-vouchers.xml");
    }

    @Test
    void tenantAIntelligenceDataDoesNotAppearInTenantB() throws Exception {
        var tokenA = registerToken("stale-a-" + System.nanoTime() + "@example.com");
        var warehouse = mapper.readTree(postJson(tokenA, "/api/warehouses", "{\"name\":\"Tenant A Warehouse\",\"code\":\"TAW\"}"));
        var product = mapper.readTree(postJson(tokenA, "/api/products", """
            {"sku":"STALE-A","name":"Secret Tenant A Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":20,"reorderPoint":5}
            """));
        postJson(tokenA, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-STALE-A","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":10,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));

        var tokenB = registerToken("stale-b-" + System.nanoTime() + "@example.com");
        var summaryB = getJson(tokenB, "/api/dashboard/summary");
        var deadStockB = getJson(tokenB, "/api/dead-stock");
        var reorderB = getJson(tokenB, "/api/reorder/suggestions");
        var aiB = postJson(tokenB, "/api/ai/assistant/chat", "{\"message\":\"Which products are dead stock?\"}");

        assertThat(summaryB).doesNotContain("Secret Tenant A Product");
        assertThat(mapper.readTree(summaryB).get("totalProducts").asInt()).isZero();
        assertThat(deadStockB).doesNotContain("Secret Tenant A Product");
        assertThat(reorderB).doesNotContain("Secret Tenant A Product");
        assertThat(aiB).doesNotContain("Secret Tenant A Product");
    }

    private String registerToken(String email) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Clean Backend Workspace","businessMode":"HYBRID","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String getJson(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String postJson(String token, String path, String body) throws Exception {
        return mvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }
}
