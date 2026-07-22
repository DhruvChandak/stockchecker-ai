package com.stockpilot.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.dev-tools.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevToolsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void devResetClearsTenantBusinessDataButKeepsUserAndTenant() throws Exception {
        var token = register("dev-reset-enabled-" + System.nanoTime() + "@example.com");
        var warehouse = postJson(token, "/api/warehouses", "{\"name\":\"Reset Warehouse\",\"code\":\"RST\"}");
        var product = postJson(token, "/api/products", """
            {"sku":"SKU-RESET","name":"Reset Product","unitCode":"PCS","defaultPurchasePrice":10,"defaultSalesPrice":15,"reorderPoint":2}
            """);
        postJson(token, "/api/customers", "{\"name\":\"Reset Customer\"}");
        postJson(token, "/api/suppliers", "{\"name\":\"Reset Supplier\"}");
        postJson(token, "/api/purchases", """
            {"warehouseId":"%s","invoiceNumber":"P-RESET","invoiceDate":"2026-01-01","items":[{"productId":"%s","quantity":2,"rate":10}]}
            """.formatted(warehouse.get("id").asText(), product.get("id").asText()));

        mvc.perform(get("/api/dev/status").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/dev/current-tenant/reset-business-data")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"RESET WORKSPACE\"}"))
            .andExpect(status().isOk());

        var products = mapper.readTree(mvc.perform(get("/api/products/search")
                .param("query", "Reset Product")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(products.get("content")).isEmpty();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(get("/api/tenants/current").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    private String register(String email) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Tenant","businessMode":"HYBRID","email":"%s","password":"password123","fullName":"Owner"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private com.fasterxml.jackson.databind.JsonNode postJson(String token, String path, String body) throws Exception {
        return mapper.readTree(mvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }
}
