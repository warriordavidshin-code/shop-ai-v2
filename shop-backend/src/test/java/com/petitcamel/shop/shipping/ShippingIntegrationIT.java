package com.petitcamel.shop.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.security.JwtService;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.provider.ShippingProvider;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import com.petitcamel.shop.shipping.service.TrackingService;
import jakarta.servlet.http.Cookie;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "shipping.provider=FAKE",
        "shipping.tracking.force-refresh-min-seconds=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ShippingIntegrationIT.FakeProviderConfig.class)
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class ShippingIntegrationIT {

    private static final long SKU_ID = 2L;
    private static final int BASE_STOCK = 30;

    /** Programmable courier response for the fake provider. */
    static final AtomicReference<Supplier<TrackingResponse>> NEXT_RESPONSE = new AtomicReference<>();

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean
        ShippingProvider fakeShippingProvider() {
            return new ShippingProvider() {
                @Override
                public String name() {
                    return "FAKE";
                }

                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public TrackingResponse tracking(String companyCode, String trackingNumber) {
                    Supplier<TrackingResponse> supplier = NEXT_RESPONSE.get();
                    return supplier == null ? TrackingResponse.notFound("미등록") : supplier.get();
                }
            };
        }
    }

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

    @Autowired
    JwtService jwtService;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        NEXT_RESPONSE.set(null);
        customerToken = signupAndGetAccessToken();
        adminToken = adminAccessToken();
        // FAKE uses the same courier codes as SweetTracker so HANJIN resolves to a provider code.
        jdbcTemplate.update("""
                INSERT INTO delivery_company_code (company_code, provider, provider_code)
                SELECT company_code, 'FAKE', provider_code FROM delivery_company_code WHERE provider = 'SWEETTRACKER'
                ON CONFLICT DO NOTHING
                """);
    }

    @AfterEach
    void tearDown() {
        cleanup();
        jdbcTemplate.update("DELETE FROM delivery_company_code WHERE provider = 'FAKE'");
    }

    @Test
    void invoiceRegistrationStartsDeliveryAndTrackingFollowsCourier() throws Exception {
        long orderId = createPaidOrder(customerToken, 2);

        mockMvc.perform(put("/api/admin/orders/{orderId}/shipment", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCompany\":\"HANJIN\",\"trackingNumber\":\"1234-5678-9012\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("송장번호가 등록되었습니다."))
                .andExpect(jsonPath("$.shipment.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.shipment.trackingNumber").value("123456789012"));
        assertThat(orderStatus(orderId)).isEqualTo("SHIPPED");

        NEXT_RESPONSE.set(() -> new TrackingResponse(true, ShipmentStatus.OUT_FOR_DELIVERY, List.of(
                new TrackingResponse.Event(Instant.parse("2026-09-29T00:00:00Z"), "서울강남", "집화처리", ShipmentStatus.PICKED_UP),
                new TrackingResponse.Event(Instant.parse("2026-09-29T04:10:00Z"), "서울강남", "배달출발",
                        ShipmentStatus.OUT_FOR_DELIVERY)), null));

        mockMvc.perform(get("/api/orders/{orderId}/tracking", orderId)
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.deliveryCompany").value("HANJIN"))
                .andExpect(jsonPath("$.trackingNumber").value("123456789012"))
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.statusName").value("배송출발"))
                .andExpect(jsonPath("$.externalTracking").value(true))
                .andExpect(jsonPath("$.events[?(@.description == '배달출발')].time").value("2026-09-29 13:10"))
                .andExpect(jsonPath("$.events[?(@.description == '배달출발')].location").value("서울강남"));

        // Within the cache window the DB copy is served even if the courier has moved on.
        NEXT_RESPONSE.set(() -> new TrackingResponse(true, ShipmentStatus.DELIVERED, List.of(
                new TrackingResponse.Event(Instant.parse("2026-09-29T06:00:00Z"), "서울강남", "배달완료",
                        ShipmentStatus.DELIVERED)), null));
        mockMvc.perform(get("/api/orders/{orderId}/tracking", orderId)
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));

        mockMvc.perform(post("/api/admin/orders/{orderId}/shipment/refresh", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("DELIVERED"));
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERED");

        // Delivered shipments are no longer polled.
        Integer due = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipment WHERE order_id = ? AND shipment_status IN ('PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY')",
                Integer.class, orderId);
        assertThat(due).isZero();
    }

    @Test
    void courierOutageDoesNotBreakTracking() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        registerInvoice(orderId, "CJ", "556677889900");
        NEXT_RESPONSE.set(() -> {
            throw new ShippingProviderException("HTTP 500");
        });

        mockMvc.perform(get("/api/orders/{orderId}/tracking", orderId)
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.message").value(TrackingService.TEMPORARY_FAILURE_MESSAGE))
                .andExpect(jsonPath("$.events.length()").value(2));

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo(orderId))
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void otherMembersCannotSeeTrackingAndCustomersCannotRegisterInvoices() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        String stranger = signupAndGetAccessToken();

        mockMvc.perform(get("/api/orders/{orderId}/tracking", orderId)
                        .cookie(new Cookie("access_token", stranger)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/orders/{orderId}/shipment", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCompany\":\"HANJIN\",\"trackingNumber\":\"123456789012\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidInvoiceInputIsRejected() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        mockMvc.perform(put("/api/admin/orders/{orderId}/shipment", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCompany\":\"없는택배\",\"trackingNumber\":\"123456789012\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/orders/{orderId}/shipment", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCompany\":\"한진택배\",\"trackingNumber\":\"12\"}"))
                .andExpect(status().isBadRequest());
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    void pickupRequestAndManualStatusFlow() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);

        mockMvc.perform(post("/api/admin/orders/{orderId}/shipment/pickup-request", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCompany\":\"LOTTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("PICKUP_REQUESTED"));
        assertThat(orderStatus(orderId)).isEqualTo("PREPARING");

        mockMvc.perform(patch("/api/admin/orders/{orderId}/shipment/status", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isBadRequest());

        registerInvoice(orderId, "LOTTE", "998877665544");
        mockMvc.perform(patch("/api/admin/orders/{orderId}/shipment/status", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("DELIVERED"));
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERED");

        mockMvc.perform(post("/api/admin/orders/{orderId}/shipment/waybill", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legacyOrderStatusChangeCreatesShipment() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        mockMvc.perform(patch("/api/admin/orders/{orderNo}/status", orderNo(orderId))
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT shipment_status FROM shipment WHERE order_id = ? AND shipment_type = 'DELIVERY'",
                String.class, orderId)).isEqualTo("PREPARING");
    }

    @Test
    void returnFlowRefundsAndRestocks() throws Exception {
        long orderId = createPaidOrder(customerToken, 2);
        String returnBody = """
                {"returnReason":"DEFECTIVE","returnMemo":"실밥 풀림","pickupName":"홍길동",
                 "pickupPhone":"010-1234-5678","pickupPostcode":"06236","pickupAddress":"서울 강남구","pickupAddressDetail":"101호"}
                """;

        mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody))
                .andExpect(status().isBadRequest());

        registerInvoice(orderId, "HANJIN", "123456789012");
        setDeliveryStatus(orderId, "DELIVERED");
        int stockBeforeReturn = stock();

        mockMvc.perform(get("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRequest").value(true))
                .andExpect(jsonPath("$.reasons.length()").value(6));

        MvcResult created = mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.freeReturn").value(true))
                .andReturn();
        long returnId = objectMapper.readTree(created.getResponse().getContentAsString()).get("returnRequestId").asLong();
        assertThat(orderStatus(orderId)).isEqualTo("RETURN_REQUESTED");

        mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody))
                .andExpect(status().is4xxClientError());

        adminPost("/api/admin/returns/{id}/approve", returnId, null)
                .andExpect(jsonPath("$.returnRequest.status").value("APPROVED"));
        adminPost("/api/admin/returns/{id}/pickup-request", returnId,
                "{\"deliveryCompany\":\"CJ\",\"trackingNumber\":\"RT0011223344\"}")
                .andExpect(jsonPath("$.returnRequest.status").value("PICKUP_REQUESTED"))
                .andExpect(jsonPath("$.shipmentStatus").value("RETURN_PICKUP_REQUESTED"));

        mockMvc.perform(get("/api/orders/{orderId}/tracking", orderId)
                        .param("type", "RETURN")
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentType").value("RETURN"))
                .andExpect(jsonPath("$.trackingNumber").value("RT0011223344"));

        mockMvc.perform(patch("/api/admin/returns/{id}/status", returnId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnRequest.status").value("RECEIVED"));

        adminPost("/api/admin/returns/{id}/refund", returnId, "{\"restock\":true,\"adminMemo\":\"검수 완료\"}")
                .andExpect(jsonPath("$.returnRequest.status").value("REFUNDED"))
                .andExpect(jsonPath("$.orderStatus").value("RETURNED"));

        assertThat(orderStatus(orderId)).isEqualTo("RETURNED");
        BigDecimal paymentAmount = jdbcTemplate.queryForObject(
                "SELECT payment_amount FROM orders WHERE order_id = ?", BigDecimal.class, orderId);
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT payment_status, refunded_amount FROM payment WHERE order_id = ?", orderId);
        assertThat(payment.get("payment_status")).isEqualTo("CANCELLED");
        assertThat((BigDecimal) payment.get("refunded_amount")).isEqualByComparingTo(paymentAmount);
        assertThat(stock()).isEqualTo(stockBeforeReturn + 2);
    }

    @Test
    void changeOfMindReturnDeductsReturnFeeAndRejectRestoresDelivered() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        registerInvoice(orderId, "HANJIN", "123456789012");
        setDeliveryStatus(orderId, "DELIVERED");

        MvcResult created = mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"returnReason":"CHANGE_OF_MIND","pickupName":"홍길동","pickupPhone":"01012345678",
                                 "pickupPostcode":"06236","pickupAddress":"서울 강남구"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        BigDecimal paymentAmount = jdbcTemplate.queryForObject(
                "SELECT payment_amount FROM orders WHERE order_id = ?", BigDecimal.class, orderId);
        assertThat(body.get("freeReturn").asBoolean()).isFalse();
        assertThat(new BigDecimal(body.get("refundAmount").asText()))
                .isEqualByComparingTo(paymentAmount.subtract(new BigDecimal("3000")));

        adminPost("/api/admin/returns/{id}/reject", body.get("returnRequestId").asLong(), "{\"reason\":\"착용 흔적\"}")
                .andExpect(jsonPath("$.returnRequest.status").value("REJECTED"));
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERED");
    }

    @Test
    void bulkRegistrationReportsPerRowResults() throws Exception {
        long first = createPaidOrder(customerToken, 1);
        long second = createPaidOrder(customerToken, 1);
        String body = objectMapper.writeValueAsString(Map.of("items", List.of(
                Map.of("orderNumber", orderNo(first), "deliveryCompany", "HANJIN", "trackingNumber", "111122223333"),
                Map.of("orderNumber", orderNo(second), "deliveryCompany", "CJ대한통운", "trackingNumber", "444455556666"),
                Map.of("orderNumber", "PC0000", "deliveryCompany", "HANJIN", "trackingNumber", "777788889999"))));

        mockMvc.perform(post("/api/admin/shipments/bulk")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.results[2].success").value(false));
        assertThat(orderStatus(first)).isEqualTo("SHIPPED");
        assertThat(orderStatus(second)).isEqualTo("SHIPPED");

        mockMvc.perform(get("/api/admin/orders")
                        .param("view", "SHIPPING")
                        .cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].shipmentStatusName").value("배송중"))
                .andExpect(jsonPath("$.content[0].memberName").value("주문자"))
                .andExpect(jsonPath("$.content[0].trackingNumber").exists());

        mockMvc.perform(get("/api/admin/dashboard")
                        .cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingInTransitCount").value(2))
                .andExpect(jsonPath("$.todayOrderCount").exists())
                .andExpect(jsonPath("$.returnRequestCount").exists());
    }

    @Test
    void shippingPolicyIsEditableAndDrivesQuotes() throws Exception {
        mockMvc.perform(get("/api/shipping/quote").param("amount", "49000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryFee").value(num(3000)));
        mockMvc.perform(get("/api/shipping/quote").param("amount", "60000").param("postcode", "63100"))
                .andExpect(jsonPath("$.deliveryFee").value(num(3000)))
                .andExpect(jsonPath("$.freeShipping").value(true))
                .andExpect(jsonPath("$.areaType").value("JEJU"));

        mockMvc.perform(put("/api/admin/shipping/policy")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseShippingFee":2500,"freeShippingAmount":100000,"jejuExtraFee":3000,
                                 "remoteAreaExtraFee":5000,"returnShippingFee":3000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeShippingAmount").value(num(100000)));

        mockMvc.perform(get("/api/shipping/quote").param("amount", "60000"))
                .andExpect(jsonPath("$.deliveryFee").value(num(2500)));
        mockMvc.perform(get("/api/shipping/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseShippingFee").value(num(2500)));
    }

    /** Matches a JSON number regardless of scale (3000 vs 3000.00). */
    private static Matcher<Object> num(long expected) {
        return new CustomMatcher<>("number " + expected) {
            @Override
            public boolean matches(Object actual) {
                return actual instanceof Number n
                        && new BigDecimal(n.toString()).compareTo(BigDecimal.valueOf(expected)) == 0;
            }
        };
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions adminPost(String url, long id, String body) throws Exception {
        var request = post(url, id).cookie(new Cookie("access_token", adminToken)).with(csrf());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private void registerInvoice(long orderId, String company, String trackingNumber) throws Exception {
        mockMvc.perform(put("/api/admin/orders/{orderId}/shipment", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCompany\":\"" + company + "\",\"trackingNumber\":\"" + trackingNumber + "\"}"))
                .andExpect(status().isOk());
    }

    private void setDeliveryStatus(long orderId, String status) throws Exception {
        mockMvc.perform(patch("/api/admin/orders/{orderId}/shipment/status", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private long createPaidOrder(String token, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", token))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"skuId":%d,"quantity":%d}],"receiverName":"홍길동","receiverPhone":"01012345678",
                                 "postcode":"06236","address1":"서울 강남구","address2":"101호"}
                                """.formatted(SKU_ID, quantity)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        mockMvc.perform(post("/api/payments/mock/approve")
                        .cookie(new Cookie("access_token", token))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + body.get("orderNo").asText() + "\"}"))
                .andExpect(status().isOk());
        return body.get("orderId").asLong();
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject("SELECT order_status FROM orders WHERE order_id = ?", String.class, orderId);
    }

    private String orderNo(long orderId) {
        return jdbcTemplate.queryForObject("SELECT order_no FROM orders WHERE order_id = ?", String.class, orderId);
    }

    private int stock() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM inventory WHERE sku_id = ?", Integer.class, SKU_ID);
        return value == null ? 0 : value;
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM shipment");
        jdbcTemplate.update("DELETE FROM return_request");
        jdbcTemplate.update("DELETE FROM payment");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        jdbcTemplate.update("DELETE FROM inventory_movement WHERE sku_id = ?", SKU_ID);
        jdbcTemplate.update(
                "UPDATE inventory SET stock_quantity = ?, reserved_quantity = 0, version = 0, updated_at = NOW() WHERE sku_id = ?",
                BASE_STOCK, SKU_ID);
        jdbcTemplate.update("""
                UPDATE shipping_policy SET base_shipping_fee = 3000, free_shipping_amount = 50000, jeju_extra_fee = 3000,
                  remote_area_extra_fee = 5000, return_shipping_fee = 3000, updated_by = NULL
                WHERE policy_id = 1
                """);
    }

    private String adminAccessToken() throws Exception {
        String token = signupAndGetAccessToken();
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
        jdbcTemplate.update("UPDATE member SET role = 'ADMIN' WHERE member_id = ?", claims.memberId());
        return jwtService.createAccessToken(claims.memberId(), claims.loginId(), MemberRole.ADMIN);
    }

    private String signupAndGetAccessToken() throws Exception {
        String loginId = ("ship" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        String email = "ship+" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("loginId", loginId),
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
