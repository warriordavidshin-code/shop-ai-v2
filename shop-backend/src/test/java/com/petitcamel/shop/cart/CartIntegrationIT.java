package com.petitcamel.shop.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class CartIntegrationIT {

    private static final long IN_STOCK_SKU = 1L;
    private static final long SOLD_OUT_SKU = 7L;

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

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        accessToken = signupAndGetAccessToken();
    }

    @Test
    void addItemSuccess() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":%d,"quantity":2}
                                """.formatted(IN_STOCK_SKU)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].skuId").value((int) IN_STOCK_SKU))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].productName").exists())
                .andExpect(jsonPath("$.items[0].optionName").exists())
                .andExpect(jsonPath("$.items[0].unitPrice").exists())
                .andExpect(jsonPath("$.items[0].availableQuantity").exists())
                .andExpect(jsonPath("$.items[0].lineTotal").exists())
                .andExpect(jsonPath("$.productAmount").exists())
                .andExpect(jsonPath("$.deliveryAmount").value(3000))
                .andExpect(jsonPath("$.paymentAmount").exists());

        mockMvc.perform(get("/api/cart")
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void addBeyondAvailableFails() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":%d,"quantity":1}
                                """.formatted(SOLD_OUT_SKU)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void updateQuantityValidation() throws Exception {
        MvcResult added = mockMvc.perform(post("/api/cart/items")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":%d,"quantity":1}
                                """.formatted(IN_STOCK_SKU)))
                .andExpect(status().isCreated())
                .andReturn();

        long cartItemId = objectMapper.readTree(added.getResponse().getContentAsString())
                .get("items").get(0).get("cartItemId").asLong();

        mockMvc.perform(patch("/api/cart/items/{id}", cartItemId)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch("/api/cart/items/{id}", cartItemId)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":9999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(patch("/api/cart/items/{id}", cartItemId)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        mockMvc.perform(delete("/api/cart/items/{id}", cartItemId)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void mergeGuestItems() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":%d,"quantity":2}
                                """.formatted(IN_STOCK_SKU)))
                .andExpect(status().isCreated());

        MvcResult merged = mockMvc.perform(post("/api/cart/merge")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"skuId":%d,"quantity":3},{"skuId":%d,"quantity":10}]}
                                """.formatted(IN_STOCK_SKU, SOLD_OUT_SKU)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        JsonNode items = objectMapper.readTree(merged.getResponse().getContentAsString()).get("items");
        assertThat(items.get(0).get("skuId").asLong()).isEqualTo(IN_STOCK_SKU);
        assertThat(items.get(0).get("quantity").asInt()).isEqualTo(5);
    }

    private String signupAndGetAccessToken() throws Exception {
        String email = "cart+" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("email", email),
                                Map.entry("password", "StrongPassword1!"),
                                Map.entry("name", "장바구니"),
                                Map.entry("birthDate", "1990-01-01"),
                                Map.entry("gender", "FEMALE"),
                                Map.entry("phone", "01012345678"),
                                Map.entry("postcode", "30100"),
                                Map.entry("address1", "세종"),
                                Map.entry("address2", "101"),
                                Map.entry("termsAgreed", true),
                                Map.entry("privacyAgreed", true)
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("access_token");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
