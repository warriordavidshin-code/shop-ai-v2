package com.petitcamel.shop.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.security.JwtService;
import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.provider.ProviderActionResult;
import com.petitcamel.shop.shipping.provider.ShipmentCommand;
import com.petitcamel.shop.shipping.provider.ShippingProviderClient;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import com.petitcamel.shop.shipping.service.TrackingService;
import jakarta.servlet.http.Cookie;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** Programmable courier tracking response for the fake vendor. */
    static final AtomicReference<Supplier<TrackingResponse>> NEXT_RESPONSE = new AtomicReference<>();
    /** Programmable waybill response for the fake vendor. */
    static final AtomicReference<Supplier<ProviderActionResult>> NEXT_WAYBILL = new AtomicReference<>();
    static final AtomicInteger WAYBILL_CALLS = new AtomicInteger();

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean
        ShippingProviderClient fakeShippingProviderClient() {
            return new ShippingProviderClient() {
                @Override
                public String code() {
                    return "FAKE";
                }

                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public boolean supports(ProviderCapability capability) {
                    return capability == ProviderCapability.TRACKING || capability == ProviderCapability.WAYBILL;
                }

                @Override
                public TrackingResponse tracking(String externalCompanyCode, String trackingNumber) {
                    Supplier<TrackingResponse> supplier = NEXT_RESPONSE.get();
                    return supplier == null ? TrackingResponse.notFound("미등록") : supplier.get();
                }

                @Override
                public ProviderActionResult issueWaybill(ShipmentCommand command) {
                    WAYBILL_CALLS.incrementAndGet();
                    return NEXT_WAYBILL.get().get();
                }

                @Override
                public String testConnection() {
                    return "FAKE 연결 성공";
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
        NEXT_WAYBILL.set(() -> new ProviderActionResult(true, "WB0000000001", "FAKE-REF-1", null, "송장이 발급되었습니다."));
        WAYBILL_CALLS.set(0);
        customerToken = signupAndGetAccessToken();
        adminToken = adminAccessToken();
        jdbcTemplate.update("""
                INSERT INTO shipping_provider (code, name, enabled, tracking_enabled, waybill_enabled, pickup_enabled,
                                               return_pickup_enabled, sort_order)
                VALUES ('FAKE', '테스트 업체', TRUE, TRUE, TRUE, FALSE, FALSE, 5)
                """);
        // FAKE uses the same courier codes as SweetTracker so HANJIN resolves to a vendor code.
        jdbcTemplate.update("""
                INSERT INTO delivery_company_provider_code (delivery_company_id, shipping_provider_id, external_company_code)
                SELECT c.delivery_company_id, f.shipping_provider_id, c.external_company_code
                FROM delivery_company_provider_code c
                JOIN shipping_provider s ON s.shipping_provider_id = c.shipping_provider_id AND s.code = 'SWEETTRACKER'
                CROSS JOIN shipping_provider f
                WHERE f.code = 'FAKE'
                """);
    }

    @AfterEach
    void tearDown() {
        cleanup();
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
        // DELIVERY shipments use the order's immutable receiver snapshot instead of copying it.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT contact_name FROM shipment WHERE order_id = ? AND shipment_type = 'DELIVERY'",
                String.class, orderId)).isNull();

        NEXT_RESPONSE.set(() -> new TrackingResponse(true, ShipmentStatus.OUT_FOR_DELIVERY, List.of(
                event("2026-09-29T00:00:00Z", "집화처리", ShipmentStatus.PICKED_UP),
                event("2026-09-29T04:10:00Z", "배달출발", ShipmentStatus.OUT_FOR_DELIVERY)), null));

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
                .andExpect(jsonPath("$.events[?(@.description == '배달출발')].location").value("서울강남"))
                .andExpect(jsonPath("$.events[?(@.description == '배달출발')].providerStatus").value("배달출발"));

        // Within the cache window the DB copy is served even if the courier has moved on.
        NEXT_RESPONSE.set(() -> new TrackingResponse(true, ShipmentStatus.DELIVERED, List.of(
                event("2026-09-29T06:00:00Z", "배달완료", ShipmentStatus.DELIVERED)), null));
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
                "SELECT COUNT(*) FROM shipment WHERE order_id = ? AND status IN ('PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY')",
                Integer.class, orderId);
        assertThat(due).isZero();
        // One notification per milestone, recorded once.
        assertThat(jdbcTemplate.queryForList(
                "SELECT event_type FROM shipping_notification WHERE order_id = ? ORDER BY shipping_notification_id",
                String.class, orderId))
                .containsExactly("DELIVERY_DISPATCHED", "DELIVERY_OUT_FOR_DELIVERY", "DELIVERY_DELIVERED");
    }

    @Test
    void repeatedTrackingNeverDuplicatesCourierEvents() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        registerInvoice(orderId, "HANJIN", "123456789012");
        NEXT_RESPONSE.set(() -> new TrackingResponse(true, ShipmentStatus.IN_TRANSIT, List.of(
                event("2026-09-29T00:00:00Z", "집화처리", ShipmentStatus.PICKED_UP),
                event("2026-09-29T03:00:00Z", "간선상차", ShipmentStatus.IN_TRANSIT)), null));

        adminRefresh(orderId);
        adminRefresh(orderId);
        assertThat(providerEventCount(orderId)).isEqualTo(2);

        NEXT_RESPONSE.set(() -> new TrackingResponse(true, ShipmentStatus.OUT_FOR_DELIVERY, List.of(
                event("2026-09-29T00:00:00Z", "집화처리", ShipmentStatus.PICKED_UP),
                event("2026-09-29T03:00:00Z", "간선상차", ShipmentStatus.IN_TRANSIT),
                event("2026-09-29T07:00:00Z", "배달출발", ShipmentStatus.OUT_FOR_DELIVERY)), null));
        adminRefresh(orderId);
        assertThat(providerEventCount(orderId)).isEqualTo(3);

        // Correcting the invoice drops the old courier's events.
        registerInvoice(orderId, "HANJIN", "210987654321");
        assertThat(providerEventCount(orderId)).isZero();
    }

    @Test
    void courierOutageDoesNotBreakTrackingAndIsLogged() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        registerInvoice(orderId, "CJ", "556677889900");
        NEXT_RESPONSE.set(() -> {
            throw new ShippingProviderException("HTTP 500", 500, "HTTP_500", false);
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipping_api_operation WHERE order_id = ? AND operation_type = 'TRACKING' "
                        + "AND status = 'FAILED' AND http_status = 500", Integer.class, orderId)).isEqualTo(1);
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
    void invalidAndDuplicateInvoiceInputIsRejected() throws Exception {
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

        long other = createPaidOrder(customerToken, 1);
        registerInvoice(other, "HANJIN", "123456789012");
        mockMvc.perform(put("/api/admin/orders/{orderId}/shipment", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCompany\":\"HANJIN\",\"trackingNumber\":\"123456789012\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 다른 배송에 등록된 송장번호입니다."));
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
        // MANUAL only guides the admin, so nothing is logged as an external call.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipping_api_operation WHERE order_id = ?", Integer.class, orderId)).isZero();

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
        assertThat(WAYBILL_CALLS.get()).isZero();
    }

    @Test
    void waybillIsIssuedOnceEvenWhenSavingFails() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        // Another shipment already holds the number the vendor will return, so saving it fails.
        long blocker = createPaidOrder(customerToken, 1);
        registerInvoice(blocker, "HANJIN", "WB0000000001");

        issueWaybill(orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("업체 처리는 완료되었지만 저장에 실패했습니다")));
        assertThat(WAYBILL_CALLS.get()).isEqualTo(1);
        Map<String, Object> op = waybillOperation(orderId);
        assertThat(op.get("status")).isEqualTo("SUCCEEDED");
        assertThat(op.get("applied_at")).isNull();
        assertThat((String) op.get("response_summary")).contains("WB0000000001").doesNotContain("홍길동");

        // After fixing the cause, the same button saves the stored result without calling the vendor again.
        registerInvoice(blocker, "HANJIN", "999988887777");
        issueWaybill(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("WAYBILL_ISSUED"))
                .andExpect(jsonPath("$.shipment.trackingNumber").value("WB0000000001"))
                .andExpect(jsonPath("$.shipment.shippingProvider").value("FAKE"));
        assertThat(WAYBILL_CALLS.get()).isEqualTo(1);
        assertThat(waybillOperation(orderId).get("applied_at")).isNotNull();
        assertThat(orderStatus(orderId)).isEqualTo("PREPARING");

        issueWaybill(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("이미 송장이 발급되었습니다."));
        assertThat(WAYBILL_CALLS.get()).isEqualTo(1);
    }

    @Test
    void unknownVendorOutcomeWaitsForAdminDecision() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        NEXT_WAYBILL.set(() -> {
            throw new ShippingProviderException("read timeout", null, "TIMEOUT", true);
        });

        issueWaybill(orderId).andExpect(status().isConflict());
        assertThat(waybillOperation(orderId).get("status")).isEqualTo("UNKNOWN");

        // The outcome is still unknown: the vendor is not called again.
        NEXT_WAYBILL.set(() -> new ProviderActionResult(true, "WB0000000002", "FAKE-REF-2", null, null));
        issueWaybill(orderId).andExpect(status().isConflict());
        assertThat(WAYBILL_CALLS.get()).isEqualTo(1);

        MvcResult list = mockMvc.perform(get("/api/admin/shipping/operations").param("status", "ATTENTION")
                        .cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].needsAttention").value(true))
                .andReturn();
        long operationId = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("content").get(0).get("operationId").asLong();

        mockMvc.perform(post("/api/admin/shipping/operations/{id}/allow-retry", operationId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        issueWaybill(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.trackingNumber").value("WB0000000002"));
        assertThat(WAYBILL_CALLS.get()).isEqualTo(2);
        assertThat(((Number) waybillOperation(orderId).get("attempt_count")).intValue()).isEqualTo(2);
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
                "SELECT status FROM shipment WHERE order_id = ? AND shipment_type = 'DELIVERY'",
                String.class, orderId)).isEqualTo("READY");
    }

    @Test
    void returnFlowRefundsAndRestocks() throws Exception {
        long orderId = createPaidOrder(customerToken, 2);
        String returnBody = """
                {"returnReason":"DEFECTIVE","returnMemo":"실밥 풀림","customerMemo":"경비실에 맡겨요","pickupName":"홍길동",
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
                .andExpect(jsonPath("$.canCancel").value(false))
                .andExpect(jsonPath("$.reasons.length()").value(6));

        MvcResult created = mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.freeReturn").value(true))
                .andExpect(jsonPath("$.customerMemo").value("경비실에 맡겨요"))
                .andExpect(jsonPath("$.pickupName").value("홍길동"))
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
                .andExpect(jsonPath("$.shipmentStatus").value("PICKUP_REQUESTED"))
                .andExpect(jsonPath("$.shipmentStatusName").value("반품수거요청"));

        mockMvc.perform(get("/api/orders/{orderId}/tracking", orderId)
                        .param("type", "RETURN")
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentType").value("RETURN"))
                .andExpect(jsonPath("$.trackingNumber").value("RT0011223344"));

        // Courier progress on the return parcel moves the return forward.
        NEXT_RESPONSE.set(() -> new TrackingResponse(true, ShipmentStatus.IN_TRANSIT, List.of(
                event("2026-09-30T01:00:00Z", "집화처리", ShipmentStatus.PICKED_UP)), null));
        mockMvc.perform(post("/api/admin/orders/{orderId}/shipment/refresh", orderId)
                        .param("type", "RETURN")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM return_request WHERE return_request_id = ?", String.class, returnId))
                .isEqualTo("IN_PROGRESS");

        mockMvc.perform(patch("/api/admin/returns/{id}/status", returnId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnRequest.status").value("RECEIVED"));

        adminPost("/api/admin/returns/{id}/refund", returnId, "{\"restock\":true,\"adminMemo\":\"검수 완료\"}")
                .andExpect(jsonPath("$.returnRequest.status").value("COMPLETED"))
                .andExpect(jsonPath("$.orderStatus").value("RETURNED"));

        assertThat(orderStatus(orderId)).isEqualTo("RETURNED");
        BigDecimal paymentAmount = jdbcTemplate.queryForObject(
                "SELECT payment_amount FROM orders WHERE order_id = ?", BigDecimal.class, orderId);
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT payment_status, refunded_amount FROM payment WHERE order_id = ?", orderId);
        assertThat(payment.get("payment_status")).isEqualTo("CANCELLED");
        assertThat((BigDecimal) payment.get("refunded_amount")).isEqualByComparingTo(paymentAmount);
        assertThat(stock()).isEqualTo(stockBeforeReturn + 2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM shipment WHERE return_request_id = ?", String.class, returnId)).isEqualTo("DELIVERED");
    }

    @Test
    void changeOfMindReturnDeductsReturnFeeAndRejectRestoresDelivered() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        registerInvoice(orderId, "HANJIN", "123456789012");
        setDeliveryStatus(orderId, "DELIVERED");

        JsonNode body = requestReturn(orderId, "CHANGE_OF_MIND");
        BigDecimal paymentAmount = jdbcTemplate.queryForObject(
                "SELECT payment_amount FROM orders WHERE order_id = ?", BigDecimal.class, orderId);
        assertThat(body.get("freeReturn").asBoolean()).isFalse();
        assertThat(new BigDecimal(body.get("refundAmount").asText()))
                .isEqualByComparingTo(paymentAmount.subtract(new BigDecimal("3000")));

        long returnId = body.get("returnRequestId").asLong();
        adminPost("/api/admin/returns/{id}/reject", returnId, "{\"reason\":\"착용 흔적\"}")
                .andExpect(jsonPath("$.returnRequest.status").value("REJECTED"))
                .andExpect(jsonPath("$.shipmentStatus").value("CANCELLED"));
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERED");
    }

    @Test
    void customerCanWithdrawReturnBeforePickupAndRequestAgain() throws Exception {
        long orderId = createPaidOrder(customerToken, 1);
        registerInvoice(orderId, "HANJIN", "123456789012");
        setDeliveryStatus(orderId, "DELIVERED");

        long firstId = requestReturn(orderId, "CHANGE_OF_MIND").get("returnRequestId").asLong();
        mockMvc.perform(get("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.canRequest").value(false));

        mockMvc.perform(post("/api/orders/{orderId}/return/cancel", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.shipmentStatus").value("CANCELLED"));
        assertThat(orderStatus(orderId)).isEqualTo("DELIVERED");

        long secondId = requestReturn(orderId, "DEFECTIVE").get("returnRequestId").asLong();
        assertThat(secondId).isNotEqualTo(firstId);
        adminPost("/api/admin/returns/{id}/approve", secondId, null);
        adminPost("/api/admin/returns/{id}/pickup-request", secondId, "{\"deliveryCompany\":\"CJ\"}");

        mockMvc.perform(post("/api/orders/{orderId}/return/cancel", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipment WHERE order_id = ? AND shipment_type = 'RETURN'", Integer.class, orderId))
                .isEqualTo(2);
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
                                 "remoteAreaExtraFee":5000,"returnShippingFee":3000,"exchangeShippingFee":6000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeShippingAmount").value(num(100000)))
                .andExpect(jsonPath("$.exchangeShippingFee").value(num(6000)));

        mockMvc.perform(get("/api/shipping/quote").param("amount", "60000"))
                .andExpect(jsonPath("$.deliveryFee").value(num(2500)));
        mockMvc.perform(get("/api/shipping/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseShippingFee").value(num(2500)));
    }

    @Test
    void extraAreaCanBeAddedDisabledAndOverridesFee() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/shipping/extra-areas")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"areaType":"REMOTE","areaName":"테스트섬","postalCodeFrom":"99990","postalCodeTo":"99999","extraFee":7000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveFee").value(7000))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andReturn();
        long areaId = objectMapper.readTree(created.getResponse().getContentAsString()).get("areaId").asLong();
        try {
            mockMvc.perform(get("/api/shipping/quote").param("amount", "10000").param("postcode", "99995"))
                    .andExpect(jsonPath("$.extraFee").value(num(7000)));

            mockMvc.perform(patch("/api/admin/shipping/extra-areas/{id}", areaId)
                            .cookie(new Cookie("access_token", adminToken))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
            mockMvc.perform(get("/api/shipping/quote").param("amount", "10000").param("postcode", "99995"))
                    .andExpect(jsonPath("$.extraFee").value(num(0)));
        } finally {
            jdbcTemplate.update("DELETE FROM shipping_extra_area WHERE shipping_extra_area_id = ?", areaId);
        }
    }

    @Test
    void providerSwitchesCodesAndConnectionTest() throws Exception {
        mockMvc.perform(get("/api/admin/shipping/providers")
                        .cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'FAKE')].activeCapabilities[*]",
                        Matchers.containsInAnyOrder("TRACKING", "WAYBILL")))
                .andExpect(jsonPath("$[?(@.code == 'MANUAL')].activeCapabilities[*]",
                        Matchers.containsInAnyOrder("PICKUP", "RETURN_PICKUP")))
                .andExpect(jsonPath("$[?(@.code == 'SWEETTRACKER')].configured").value(false));

        mockMvc.perform(patch("/api/admin/shipping/providers/{code}", "MANUAL")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/shipping/providers/{code}", "FAKE")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waybillEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waybillEnabled").value(false));
        long orderId = createPaidOrder(customerToken, 1);
        issueWaybill(orderId).andExpect(status().isBadRequest());
        assertThat(WAYBILL_CALLS.get()).isZero();

        mockMvc.perform(put("/api/admin/shipping/delivery-companies/{code}/provider-codes/{provider}", "HANJIN", "FAKE")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalCompanyCode\":\"HJ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerCodes.FAKE").value("HJ"));
        mockMvc.perform(put("/api/admin/shipping/delivery-companies/{code}/provider-codes/{provider}", "HANJIN", "FAKE")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalCompanyCode\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerCodes.FAKE").doesNotExist());

        mockMvc.perform(post("/api/admin/shipping/providers/{code}/test", "FAKE")
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("FAKE 연결 성공"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipping_api_operation WHERE provider_code = 'FAKE' AND operation_type = 'CONNECTION_TEST'",
                Integer.class)).isEqualTo(1);

        mockMvc.perform(get("/api/admin/shipping/integration")
                        .cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedProvider").value("FAKE"))
                .andExpect(jsonPath("$.activeProvider").value("FAKE"))
                .andExpect(jsonPath("$.pickupService").value("MANUAL"))
                .andExpect(jsonPath("$.waybillSupported").value(false));
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

    private static TrackingResponse.Event event(String time, String kind, ShipmentStatus status) {
        return new TrackingResponse.Event(Instant.parse(time), null, kind, status, "서울강남", kind);
    }

    private ResultActions adminPost(String url, long id, String body) throws Exception {
        var request = post(url, id).cookie(new Cookie("access_token", adminToken)).with(csrf());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private ResultActions issueWaybill(long orderId) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/{orderId}/shipment/waybill", orderId)
                .cookie(new Cookie("access_token", adminToken))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryCompany\":\"HANJIN\"}"));
    }

    private void adminRefresh(long orderId) throws Exception {
        mockMvc.perform(post("/api/admin/orders/{orderId}/shipment/refresh", orderId)
                        .cookie(new Cookie("access_token", adminToken))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private Map<String, Object> waybillOperation(long orderId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, applied_at, response_summary, attempt_count FROM shipping_api_operation WHERE idempotency_key = ?",
                "WAYBILL:" + orderId + ":DELIVERY");
    }

    private int providerEventCount(long orderId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM shipment_tracking_event e JOIN shipment s ON s.shipment_id = e.shipment_id
                WHERE s.order_id = ? AND s.shipment_type = 'DELIVERY' AND e.source = 'PROVIDER'
                """, Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private JsonNode requestReturn(long orderId, String reason) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/orders/{orderId}/return", orderId)
                        .cookie(new Cookie("access_token", customerToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"returnReason":"%s","pickupName":"홍길동","pickupPhone":"01012345678",
                                 "pickupPostcode":"06236","pickupAddress":"서울 강남구"}
                                """.formatted(reason)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString());
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
        jdbcTemplate.update("DELETE FROM shipping_api_operation");
        jdbcTemplate.update("DELETE FROM shipping_notification");
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
                UPDATE shipping_policy SET base_shipping_fee = 3000, free_shipping_threshold = 50000, jeju_extra_fee = 3000,
                  remote_area_extra_fee = 5000, return_shipping_fee = 3000, exchange_shipping_fee = 6000
                WHERE enabled
                """);
        jdbcTemplate.update("DELETE FROM shipping_provider WHERE code = 'FAKE'");
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
