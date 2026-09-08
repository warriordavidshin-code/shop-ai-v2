package com.petitcamel.shop.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class ProductCatalogIT {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/shop_ai"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DB_USERNAME", "shop_ai"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DB_PASSWORD", "shop_ai123!"));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void listProductsReturnsSampleData() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void filterByKeyword() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "원피스"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].productName").value(org.hamcrest.Matchers.containsString("원피스")));
    }

    @Test
    void productDetailIncludesSkusAndAvailableQuantity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.skus").isArray())
                .andExpect(jsonPath("$.skus.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.skus[0].availableQuantity").exists())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.measurements").isArray())
                .andReturn();

        JsonNode skus = objectMapper.readTree(result.getResponse().getContentAsString()).get("skus");
        assertThat(skus).isNotEmpty();
        assertThat(skus.get(0).has("availableQuantity")).isTrue();
    }

    @Test
    void soldOutSkuHasZeroAvailableQuantity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/3"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode skus = objectMapper.readTree(result.getResponse().getContentAsString()).get("skus");
        JsonNode soldOutSku = null;
        for (JsonNode sku : skus) {
            if ("PC-PANTS-BG-L".equals(sku.get("skuCode").asText())) {
                soldOutSku = sku;
                break;
            }
        }
        assertThat(soldOutSku).as("PC-PANTS-BG-L SKU").isNotNull();
        assertThat(soldOutSku.get("availableQuantity").asInt()).isZero();
    }

    @Test
    void customerCanGetProductsWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].categoryId").exists())
                .andExpect(jsonPath("$[0].children").isArray());
    }
}
