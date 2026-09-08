package com.petitcamel.shop.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.Map;
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
class OrderPaymentIT {

    private static final long SKU_ID = 2L;
    private static final long SOLD_OUT_SKU = 7L;
    private static final int BASE_STOCK = 15;
    private static final BigDecimal UNIT_PRICE = new BigDecimal("49000.00");

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
        cleanupOrders();
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = ?, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                BASE_STOCK, SKU_ID);
        jdbcTemplate.update("DELETE FROM inventory_movement WHERE sku_id = ?", SKU_ID);
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        accessToken = signupAndGetAccessToken();
    }

    @AfterEach
    void tearDown() {
        cleanupOrders();
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = ?, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                BASE_STOCK, SKU_ID);
    }

    @Test
    void createOrderSuccessWithServerCalculatedAmount() throws Exception {
        int quantity = 2;
        BigDecimal productAmount = UNIT_PRICE.multiply(BigDecimal.valueOf(quantity));
        BigDecimal delivery = new BigDecimal("3000.00");
        BigDecimal paymentAmount = productAmount.add(delivery);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", accessToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(SKU_ID, quantity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo").exists())
                .andExpect(jsonPath("$.orderStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("READY"))
                .andExpect(jsonPath("$.paymentMethod").value("MOCK"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").exists())
                .andExpect(jsonPath("$.items[0].optionName").exists())
                .andExpect(jsonPath("$.items[0].unitPrice").value(49000.0))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(new BigDecimal(body.get("totalProductAmount").asText())).isEqualByComparingTo(productAmount);
        assertThat(new BigDecimal(body.get("discountAmount").asText())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(body.get("deliveryAmount").asText())).isEqualByComparingTo(delivery);
        assertThat(new BigDecimal(body.get("paymentAmount").asText())).isEqualByComparingTo(paymentAmount);

        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(reserved).isEqualTo(quantity);
        assertThat(stock).isEqualTo(BASE_STOCK);

        Integer reservationMovements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movement WHERE sku_id = ? AND movement_type = 'RESERVATION'",
                Integer.class, SKU_ID);
        assertThat(reservationMovements).isEqualTo(1);
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameOrderNo() throws Exception {
        String key = "idem-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", accessToken))
                        .header("Idempotency-Key", key)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(SKU_ID, 1)))
                .andExpect(status().isCreated())
                .andReturn();

        String orderNo = objectMapper.readTree(first.getResponse().getContentAsString()).get("orderNo").asText();

        mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", accessToken))
                        .header("Idempotency-Key", key)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(SKU_ID, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo").value(orderNo));

        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(reserved).isEqualTo(1);

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE idempotency_key = ?", Integer.class, key);
        assertThat(orderCount).isEqualTo(1);
    }

    @Test
    void mockApproveSucceedsAndStockDecreases() throws Exception {
        int quantity = 3;
        String orderNo = createOrder(SKU_ID, quantity);

        mockMvc.perform(post("/api/payments/mock/approve")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.providerTransactionId").exists());

        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(stock).isEqualTo(BASE_STOCK - quantity);
        assertThat(reserved).isZero();

        Integer saleMovements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_movement WHERE sku_id = ? AND movement_type = 'SALE'",
                Integer.class, SKU_ID);
        assertThat(saleMovements).isEqualTo(1);

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo)
                        .cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"));
    }

    @Test
    void cancelRestoresInventory() throws Exception {
        int quantity = 2;
        String pendingOrderNo = createOrder(SKU_ID, quantity);

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", pendingOrderNo)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        Integer reservedAfterPendingCancel = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        Integer stockAfterPendingCancel = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(reservedAfterPendingCancel).isZero();
        assertThat(stockAfterPendingCancel).isEqualTo(BASE_STOCK);

        String paidOrderNo = createOrder(SKU_ID, quantity);
        mockMvc.perform(post("/api/payments/mock/approve")
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + paidOrderNo + "\"}"))
                .andExpect(status().isOk());

        Integer stockAfterPaid = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(stockAfterPaid).isEqualTo(BASE_STOCK - quantity);

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", paidOrderNo)
                        .cookie(new Cookie("access_token", accessToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));

        Integer stockRestored = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        Integer reservedFinal = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(stockRestored).isEqualTo(BASE_STOCK);
        assertThat(reservedFinal).isZero();
    }

    @Test
    void cannotCreateOrderWhenAvailableQuantityInsufficient() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", accessToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(SOLD_OUT_SKU, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", accessToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(SKU_ID, BASE_STOCK + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        assertThat(reserved).isZero();
    }

    private String createOrder(long skuId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", accessToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(skuId, quantity)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("orderNo").asText();
    }

    private String orderBody(long skuId, int quantity) {
        return """
                {
                  "items":[{"skuId":%d,"quantity":%d}],
                  "receiverName":"홍길동",
                  "receiverPhone":"01012345678",
                  "postcode":"06236",
                  "address1":"서울 강남구",
                  "address2":"101호",
                  "orderMemo":"문 앞에 두세요"
                }
                """.formatted(skuId, quantity);
    }

    private void cleanupOrders() {
        jdbcTemplate.update("DELETE FROM payment");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM orders");
    }

    private String signupAndGetAccessToken() throws Exception {
        String email = "order+" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("email", email),
                                Map.entry("password", "StrongPassword1!"),
                                Map.entry("name", "주문자"),
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
