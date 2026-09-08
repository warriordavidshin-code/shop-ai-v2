package com.petitcamel.shop.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.recommendation.provider.OutfitRecommendationProvider;
import com.petitcamel.shop.recommendation.provider.RuleBasedOutfitRecommendationProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class ReviewAiIT {

    private static final long PRODUCT_ID = 1L;
    private static final long SKU_ID = 2L;
    private static final int BASE_STOCK = 15;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/shop_ai"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DB_USERNAME", "shop_ai"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DB_PASSWORD", "shop_ai123!"));
        registry.add("app.ai.provider", () -> "rule");
        registry.add("app.ai.openai.api-key", () -> "");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutfitRecommendationProvider outfitRecommendationProvider;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = ?, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                BASE_STOCK, SKU_ID);
        accessToken = signupAndGetAccessToken();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = ?, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                BASE_STOCK, SKU_ID);
    }

    @Test
    void cannotReviewWithoutPaidPurchase() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/reviews", PRODUCT_ID)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": 999999,
                                  "rating": 5,
                                  "content": "좋아요"
                                }
                                """))
                .andExpect(status().isNotFound());

        String orderNo = createOrder(SKU_ID, 1);
        Long orderItemId = jdbcTemplate.queryForObject(
                """
                        SELECT oi.order_item_id FROM order_item oi
                        JOIN orders o ON o.order_id = oi.order_id
                        WHERE o.order_no = ?
                        """,
                Long.class, orderNo);

        mockMvc.perform(post("/api/products/{productId}/reviews", PRODUCT_ID)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": %d,
                                  "rating": 5,
                                  "content": "결제 전 리뷰"
                                }
                                """.formatted(orderItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void canReviewAfterPaidOrder() throws Exception {
        String orderNo = createOrder(SKU_ID, 1);
        mockMvc.perform(post("/api/payments/mock/approve")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"));

        Long orderItemId = jdbcTemplate.queryForObject(
                """
                        SELECT oi.order_item_id FROM order_item oi
                        JOIN orders o ON o.order_id = oi.order_id
                        WHERE o.order_no = ?
                        """,
                Long.class, orderNo);

        mockMvc.perform(post("/api/products/{productId}/reviews", PRODUCT_ID)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": %d,
                                  "rating": 4,
                                  "content": "핏이 좋아요",
                                  "heightCm": 165,
                                  "weightKg": 52,
                                  "purchasedSize": "M",
                                  "fitRating": "TRUE_TO_SIZE"
                                }
                                """.formatted(orderItemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.orderItemId").value(orderItemId.intValue()))
                .andExpect(jsonPath("$.status").value("VISIBLE"));

        mockMvc.perform(get("/api/products/{productId}/reviews", PRODUCT_ID)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("핏이 좋아요"));
    }

    @Test
    void sizeRecommendationReturnsStructureAndDoesNotInventSku() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/size-recommendations")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "heightCm": 165,
                                  "weightKg": 55,
                                  "preferredFit": "REGULAR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.confidence").exists())
                .andExpect(jsonPath("$.reasons").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String confidence = body.get("confidence").asText();
        assertThat(confidence).isIn("HIGH", "MEDIUM", "LOW", "UNCERTAIN");

        if (!body.get("recommendedSize").isNull()) {
            String size = body.get("recommendedSize").asText();
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM product_measurement WHERE product_id = 1 AND size = ?",
                    Integer.class, size);
            assertThat(count).isEqualTo(1);
            assertThat(size).isNotEqualTo("XXL-FAKE");
        }
    }

    @Test
    void outfitRecommendationOnlyIncludesInStockOnSaleProducts() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/outfit-recommendations")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "occasion": "daily",
                                  "style": "casual",
                                  "colors": ["Ivory", "Beige", "Camel", "Cream"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").exists())
                .andExpect(jsonPath("$.provider").value("rule"))
                .andExpect(jsonPath("$.outfits").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Set<Long> productIds = new HashSet<>();
        for (JsonNode outfit : body.get("outfits")) {
            for (JsonNode item : outfit.get("items")) {
                long productId = item.get("productId").asLong();
                productIds.add(productId);
                assertThat(item.get("soldOut").asBoolean()).isFalse();

                String status = jdbcTemplate.queryForObject(
                        "SELECT status FROM product WHERE product_id = ?", String.class, productId);
                assertThat(status).isEqualTo("ON_SALE");

                Integer available = jdbcTemplate.queryForObject(
                        """
                                SELECT COALESCE(SUM(i.stock_quantity - i.reserved_quantity), 0)
                                FROM product_sku s
                                JOIN inventory i ON i.sku_id = s.sku_id
                                WHERE s.product_id = ? AND s.status = 'ON_SALE'
                                """,
                        Integer.class, productId);
                assertThat(available).isGreaterThan(0);
            }
        }
        assertThat(productIds).doesNotContain(999999L);
    }

    @Test
    void ruleProviderUsedWhenNoOpenaiKey() {
        assertThat(outfitRecommendationProvider)
                .isInstanceOf(RuleBasedOutfitRecommendationProvider.class);
    }

    private String createOrder(long skuId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", accessToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"skuId":%d,"quantity":%d}],
                                  "receiverName":"홍길동",
                                  "receiverPhone":"01012345678",
                                  "postcode":"06236",
                                  "address1":"서울 강남구",
                                  "address2":"101호",
                                  "orderMemo":"리뷰 IT"
                                }
                                """.formatted(skuId, quantity)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("orderNo").asText();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM recommendation_event");
        jdbcTemplate.update("DELETE FROM ai_recommendation");
        jdbcTemplate.update("DELETE FROM review");
        jdbcTemplate.update("DELETE FROM payment");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
    }

    private String signupAndGetAccessToken() throws Exception {
        String email = "review-ai+" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("email", email),
                                Map.entry("password", "StrongPassword1!"),
                                Map.entry("name", "리뷰어"),
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
