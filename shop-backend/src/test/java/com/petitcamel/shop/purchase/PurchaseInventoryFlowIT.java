package com.petitcamel.shop.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.analytics.dto.AnalyticsDtos;
import com.petitcamel.shop.analytics.service.ProfitAnalyticsService;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.cost.dto.CostDtos;
import com.petitcamel.shop.cost.service.ProductCostService;
import com.petitcamel.shop.dashboard.OperationsDashboardService;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.dto.AdminProductRequest;
import com.petitcamel.shop.product.dto.AdminProductResponse;
import com.petitcamel.shop.product.service.AdminProductService;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.KeptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.MatchStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.PaymentMethod;
import com.petitcamel.shop.purchase.dto.PurchaseDtos;
import com.petitcamel.shop.purchase.dto.ReceivingDtos;
import com.petitcamel.shop.purchase.service.PurchaseItemMatchingService;
import com.petitcamel.shop.purchase.service.PurchaseLedgerService;
import com.petitcamel.shop.purchase.service.ReceivingService;
import com.petitcamel.shop.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 매입 → 매칭 → 입고 → 재고/원가 → 판매 → 손익 → 대시보드 end-to-end against a throwaway PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class PurchaseInventoryFlowIT {

    private static final String SKU_CODE = "IT-PUR-BLK-F";
    private static final String PRODUCT_NAME = "IT 매입 테스트 원피스";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/shop_ai"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DB_USERNAME", "shop_ai"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DB_PASSWORD", "shop_ai123!"));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;
    @Autowired AdminProductService adminProductService;
    @Autowired PurchaseLedgerService ledgerService;
    @Autowired PurchaseItemMatchingService matchingService;
    @Autowired ReceivingService receivingService;
    @Autowired ProductCostService costService;
    @Autowired ProfitAnalyticsService profitService;
    @Autowired OperationsDashboardService dashboardService;

    private long adminId;
    private String adminToken;
    private String customerToken;
    private long productId;
    private long skuId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        String token = signup();
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE member_id = ?", claims.memberId());
        adminId = claims.memberId();
        adminToken = jwtService.createAccessToken(claims.memberId(), claims.loginId(), MemberRole.ADMIN);
        customerToken = signup();

        AdminProductResponse product = adminProductService.createProduct(new AdminProductRequest(3L, PRODUCT_NAME,
                "IT", "요약", "설명", new BigDecimal("39000"), new BigDecimal("39000"), ProductStatus.ON_SALE,
                null, null, null, null, null, null, null,
                List.of(new AdminProductRequest.SkuRequest(null, SKU_CODE, "블랙", "FREE", null, null, 0)),
                null, null, null), adminId);
        productId = product.productId();
        skuId = product.skus().get(0).skuId();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void purchaseDoesNotChangeStockUntilReceiptIsConfirmedOnce() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        PurchaseDtos.LedgerDetail first = createLedger(today, "SUP-001", 10, 18000L);
        String ym = today.toString().substring(0, 7).replace("-", "");
        assertThat(first.ledger().managementNo()).isEqualTo(ym + "-001");
        assertThat(stock()).isZero();
        PurchaseDtos.ItemResponse item = first.items().get(0);
        assertThat(item.matchStatus()).isIn(MatchStatus.CANDIDATE, MatchStatus.NEW_PRODUCT);

        assertThatThrownBy(() -> receivingService.create(new ReceivingDtos.CreateReceiptRequest(
                first.ledger().id(), null, null, null, null, null, null, null, null), adminId))
                .isInstanceOf(BusinessException.class);

        matchingService.match(new PurchaseItemMatchingService.MatchRequest(first.ledger().id(), item.id(),
                PurchaseItemMatchingService.Action.CONFIRM, productId, skuId, null, null, true), adminId);

        ReceivingDtos.ReceiptResponse receipt = receivingService.create(new ReceivingDtos.CreateReceiptRequest(
                first.ledger().id(), null, null, null, null, null, null, null, null), adminId);
        assertThat(receipt.receiptNo()).matches("RC\\d{8}-[A-Z2-9]{5}");
        assertThat(receipt.items()).hasSize(1);
        assertThat(receipt.items().get(0).expectedQuantity()).isEqualTo(10);
        assertThat(stock()).isZero();

        ReceivingDtos.ReceiptResponse confirmed = receivingService.confirm(receipt.id(),
                new ReceivingDtos.ConfirmReceiptRequest(List.of(new ReceivingDtos.ConfirmLine(
                        receipt.items().get(0).id(), 8)), null, null, null, null, null, null), adminId);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(stock()).isEqualTo(8);
        assertThat(confirmed.items().get(0).ledgerRemainingQuantity()).isEqualTo(2);
        assertThat(confirmed.warnings()).anyMatch(w -> w.contains("미입고 2"));

        ReceivingDtos.ReceiptResponse again = receivingService.confirm(receipt.id(), null, adminId);
        assertThat(again.alreadyConfirmed()).isTrue();
        assertThat(stock()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement WHERE sku_id = ? AND movement_type = 'RECEIPT'",
                Integer.class, skuId)).isEqualTo(1);
        Map<String, Object> movement = jdbc.queryForMap(
                "SELECT stock_before, stock_after, reference_type, purchase_receipt_id, product_id FROM inventory_movement "
                        + "WHERE sku_id = ? AND movement_type = 'RECEIPT'", skuId);
        assertThat(movement.get("stock_before")).isEqualTo(0);
        assertThat(movement.get("stock_after")).isEqualTo(8);
        assertThat(movement.get("reference_type")).isEqualTo("PURCHASE_RECEIPT_ITEM");
        assertThat(((Number) movement.get("product_id")).longValue()).isEqualTo(productId);

        ReceivingDtos.ReceiptResponse rest = receivingService.create(new ReceivingDtos.CreateReceiptRequest(
                first.ledger().id(), null, null, null, null, null, null, null, null), adminId);
        assertThat(rest.items().get(0).expectedQuantity()).isEqualTo(2);
        receivingService.confirm(rest.id(), null, adminId);
        assertThat(stock()).isEqualTo(10);

        // restock at a different price; remembered supplier code matches automatically
        PurchaseDtos.LedgerDetail second = createLedger(today, "SUP-001", 10, 20000L);
        assertThat(second.ledger().managementNo()).isEqualTo(ym + "-002");
        assertThat(second.items().get(0).matchStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(second.items().get(0).matchedSkuId()).isEqualTo(skuId);
        ReceivingDtos.ReceiptResponse restock = receivingService.create(new ReceivingDtos.CreateReceiptRequest(
                second.ledger().id(), null, null, null, null, null, null, null, null), adminId);
        receivingService.confirm(restock.id(), null, adminId);
        assertThat(stock()).isEqualTo(20);

        CostDtos.CostSummaryResponse cost = costService.summary(productId);
        assertThat(cost.movingAverageCost()).isEqualByComparingTo("19000");
        assertThat(cost.recentCost()).isEqualByComparingTo("20000");
        assertThat(cost.simpleWeightedAverageCost()).isEqualByComparingTo("19000");
        assertThat(costService.history(productId)).hasSize(3);

        // ledger with confirmed receipts cannot be deleted
        assertThatThrownBy(() -> ledgerService.delete(first.ledger().id(), adminId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentConfirmIncreasesStockOnlyOnce() throws Exception {
        PurchaseDtos.LedgerDetail ledger = createLedger(LocalDate.now(ZONE), "SUP-002", 5, 15000L);
        matchingService.match(new PurchaseItemMatchingService.MatchRequest(ledger.ledger().id(),
                ledger.items().get(0).id(), PurchaseItemMatchingService.Action.CONFIRM, productId, skuId, null, null,
                false), adminId);
        ReceivingDtos.ReceiptResponse receipt = receivingService.create(new ReceivingDtos.CreateReceiptRequest(
                ledger.ledger().id(), null, 3000L, null, null, null, null, null, null), adminId);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<ReceivingDtos.ReceiptResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<ReceivingDtos.ReceiptResponse> task = () -> receivingService.confirm(receipt.id(), null, adminId);
            futures.add(pool.submit(task));
        }
        int fresh = 0;
        for (Future<ReceivingDtos.ReceiptResponse> future : futures) {
            try {
                if (!future.get().alreadyConfirmed()) {
                    fresh++;
                }
            } catch (Exception ignored) {
                // a losing transaction may surface a conflict; stock must still be applied once
            }
        }
        pool.shutdown();
        assertThat(fresh).isEqualTo(1);
        assertThat(stock()).isEqualTo(5);
        BigDecimal landed = jdbc.queryForObject(
                "SELECT landed_unit_cost FROM product_cost_history WHERE product_id = ?", BigDecimal.class, productId);
        assertThat(landed).isEqualByComparingTo("15600");
    }

    @Test
    void saleSnapshotsCostAndProfitNetsCancellationWithoutDoubleRestore() throws Exception {
        PurchaseDtos.LedgerDetail ledger = createLedger(LocalDate.now(ZONE), "SUP-003", 10, 18000L);
        matchingService.match(new PurchaseItemMatchingService.MatchRequest(ledger.ledger().id(),
                ledger.items().get(0).id(), PurchaseItemMatchingService.Action.CONFIRM, productId, skuId, null, null,
                false), adminId);
        ReceivingDtos.ReceiptResponse receipt = receivingService.create(new ReceivingDtos.CreateReceiptRequest(
                ledger.ledger().id(), null, null, null, null, null, null, null, null), adminId);
        receivingService.confirm(receipt.id(), null, adminId);
        assertThat(stock()).isEqualTo(10);

        String sold = createAndPayOrder(2);
        assertThat(stock()).isEqualTo(8);
        Map<String, Object> line = jdbc.queryForMap("SELECT oi.unit_cost, oi.cost_method FROM order_item oi "
                + "JOIN orders o ON o.order_id = oi.order_id WHERE o.order_no = ?", sold);
        assertThat((BigDecimal) line.get("unit_cost")).isEqualByComparingTo("18000");
        assertThat(line.get("cost_method")).isEqualTo("WEIGHTED_AVERAGE");

        String cancelled = createAndPayOrder(1);
        assertThat(stock()).isEqualTo(7);
        cancelPaidOrder(cancelled);
        assertThat(stock()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement WHERE sku_id = ? AND movement_type = 'CANCEL' "
                + "AND stock_after - stock_before = 1", Integer.class, skuId)).isEqualTo(1);

        // later cost changes must not rewrite past profit
        jdbc.update("UPDATE sku_cost SET avg_unit_cost = 99999 WHERE sku_id = ?", skuId);

        LocalDate today = LocalDate.now(ZONE);
        AnalyticsDtos.ProductProfitResponse profit = profitService.productProfit(
                profitService.resolvePeriod("custom", today, today));
        AnalyticsDtos.ProfitLine productLine = profit.products().stream()
                .filter(p -> p.productId().equals(productId)).findFirst().orElseThrow();
        assertThat(productLine.soldQuantity()).isEqualTo(2);
        assertThat(productLine.refundedQuantity()).isEqualTo(1);
        assertThat(productLine.grossSales()).isEqualByComparingTo("117000");
        assertThat(productLine.refundAmount()).isEqualByComparingTo("39000");
        assertThat(productLine.netSales()).isEqualByComparingTo("78000");
        assertThat(productLine.costOfGoodsSold()).isEqualByComparingTo("36000");
        assertThat(productLine.grossProfit()).isEqualByComparingTo("42000");
        BigDecimal soldPayment = jdbc.queryForObject("SELECT payment_amount FROM orders WHERE order_no = ?",
                BigDecimal.class, sold);
        assertThat(productLine.paymentFee()).isEqualByComparingTo(
                soldPayment.multiply(new BigDecimal("0.033")).setScale(0, java.math.RoundingMode.HALF_UP));
        assertThat(productLine.shippingCost()).isEqualByComparingTo("3000");
        assertThat(productLine.contributionProfit()).isEqualByComparingTo(productLine.grossProfit()
                .add(productLine.deliveryRevenue()).subtract(productLine.paymentFee())
                .subtract(productLine.shippingCost()).subtract(productLine.otherSellingCost()));

        OperationsDashboardService.DashboardResponse dashboard = dashboardService.operations();
        AnalyticsDtos.ProfitTotals month = profitService.totals(today.withDayOfMonth(1), today);
        assertThat(dashboard.sales().netSales()).isEqualByComparingTo(month.netSales());
        assertThat(dashboard.sales().grossProfit()).isEqualByComparingTo(month.grossProfit());
        Long monthPurchases = jdbc.queryForObject("SELECT COALESCE(SUM(purchase_amount), 0) FROM purchase_ledger "
                + "WHERE deleted_at IS NULL AND purchase_date BETWEEN ? AND ?", Long.class,
                today.withDayOfMonth(1), today);
        assertThat(dashboard.purchases().purchaseAmount()).isEqualTo(monthPurchases);
        assertThat(dashboard.stock().totalQuantity()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void adminApisRequireAdminAndAiWithoutKeyReturns503() throws Exception {
        for (String url : List.of("/api/admin/purchases", "/api/admin/inventory/receipts", "/api/admin/inventory/low-stock",
                "/api/admin/analytics/product-profit", "/api/admin/dashboard/operations", "/api/admin/pricing/policies",
                "/api/admin/suppliers", "/api/admin/ai/analysis-status")) {
            mockMvc.perform(get(url).cookie(new Cookie("access_token", customerToken))).andExpect(status().isForbidden());
            mockMvc.perform(get(url).cookie(new Cookie("access_token", adminToken))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/admin/purchases")).andExpect(status().isUnauthorized());

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        mockMvc.perform(multipart("/api/admin/purchases/ai/analyze")
                        .file(new MockMultipartFile("files", "r.png", "image/png", png))
                        .cookie(new Cookie("access_token", adminToken)).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("수동 입력")));
        mockMvc.perform(get("/api/admin/ai/analysis-status").cookie(new Cookie("access_token", adminToken)))
                .andExpect(jsonPath("$.apiKeyConfigured").value(false))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void exportUsesTemplateAndEvidenceIsPrivate() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MvcResult upload = mockMvc.perform(multipart("/api/admin/purchases/evidence")
                        .file(new MockMultipartFile("files", "statement.png", "image/png", png))
                        .param("category", "STATEMENT")
                        .cookie(new Cookie("access_token", adminToken)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        JsonNode evidence = objectMapper.readTree(upload.getResponse().getContentAsString()).get(0);
        long evidenceId = evidence.get("id").asLong();

        mockMvc.perform(multipart("/api/admin/purchases/evidence")
                        .file(new MockMultipartFile("files", "fake.png", "image/png", "<html>".getBytes()))
                        .cookie(new Cookie("access_token", adminToken)).with(csrf()))
                .andExpect(status().isBadRequest());

        PurchaseDtos.LedgerDetail ledger = ledgerService.create(new PurchaseDtos.LedgerRequest(today, null,
                "IT엑셀거래처", "220-81-62517", null, 50000L, null, null, 50000L, today, PaymentMethod.TRANSFER,
                "홍길동", "TX-1", KeptStatus.KEPT, KeptStatus.KEPT, null, null, null,
                List.of(new PurchaseDtos.ItemRequest(null, "엑셀 테스트 상품", null, "아이보리", "M", null, 5, 10000L,
                        null, null, 50000L)), List.of(evidenceId), null, false, null), adminId);
        assertThat(ledger.evidences()).hasSize(1);
        assertThat(ledger.ledger().verificationLabel()).isEqualTo("기본자료 보관");

        assertThatThrownBy(() -> ledgerService.create(new PurchaseDtos.LedgerRequest(today, null,
                "IT엑셀거래처", null, null, 50000L, null, null, 50000L, today, PaymentMethod.TRANSFER,
                null, "TX-1", null, null, null, null, null, null, null, null, false, null), adminId))
                .isInstanceOf(BusinessException.class);

        String storedPath = jdbc.queryForObject("SELECT stored_path FROM purchase_evidence WHERE purchase_evidence_id = ?",
                String.class, evidenceId);
        assertThat(storedPath).startsWith("purchase-evidence/");
        mockMvc.perform(get("/uploads/" + storedPath)).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/admin/purchases/evidence/{id}/file", evidenceId)
                        .cookie(new Cookie("access_token", customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/purchases/evidence/{id}/file", evidenceId)
                        .cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        String month = today.toString().substring(0, 7);
        MvcResult export = mockMvc.perform(get("/api/admin/purchases/export").param("scope", "month")
                        .param("month", month).cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andReturn();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            var sheet = wb.getSheet("매입장부");
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo(ledger.ledger().managementNo());
            assertThat(sheet.getRow(5).getCell(4).getStringCellValue()).contains("엑셀 테스트 상품");
            assertThat(sheet.getRow(5).getCell(14).getStringCellValue()).contains("statement.png");
            assertThat(sheet.getRow(5).getCell(10).getCellFormula()).isEqualTo("IF(F6=\"\",\"\",F6-G6)");
        }
    }

    private PurchaseDtos.LedgerDetail createLedger(LocalDate date, String code, int qty, long unitPrice) {
        long total = qty * unitPrice;
        return ledgerService.create(new PurchaseDtos.LedgerRequest(date, null, "IT거래처", null, null, total, null,
                null, total, date, PaymentMethod.TRANSFER, "IT예금주", "TX-" + UUID.randomUUID(), KeptStatus.KEPT,
                KeptStatus.KEPT, null, null, null,
                List.of(new PurchaseDtos.ItemRequest(null, PRODUCT_NAME, null, "블랙", "FREE", code, qty, unitPrice, null,
                        null, total)), null, null, true, null), adminId);
    }

    private int stock() {
        Integer value = jdbc.queryForObject("SELECT COALESCE((SELECT stock_quantity FROM inventory WHERE sku_id = ?), 0)",
                Integer.class, skuId);
        return value == null ? 0 : value;
    }

    private String createAndPayOrder(int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .cookie(new Cookie("access_token", customerToken))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"skuId":%d,"quantity":%d}],"receiverName":"홍길동","receiverPhone":"01012345678",
                                 "postcode":"06236","address1":"서울 강남구","address2":"101호"}
                                """.formatted(skuId, quantity)))
                .andExpect(status().isCreated()).andReturn();
        String orderNo = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderNo").asText();
        mockMvc.perform(post("/api/payments/mock/approve")
                        .cookie(new Cookie("access_token", customerToken)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isOk());
        return orderNo;
    }

    private void cancelPaidOrder(String orderNo) throws Exception {
        mockMvc.perform(post("/api/orders/{orderNo}/cancel-request", orderNo)
                        .cookie(new Cookie("access_token", customerToken)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isOk());
        Long requestId = jdbc.queryForObject("SELECT r.cancel_request_id FROM order_cancel_request r JOIN orders o "
                + "ON o.order_id = r.order_id WHERE o.order_no = ? AND r.status = 'REQUESTED'", Long.class, orderNo);
        mockMvc.perform(post("/api/admin/order-cancel-requests/{id}/approve", requestId)
                        .cookie(new Cookie("access_token", adminToken)).with(csrf()))
                .andExpect(status().isOk());
    }

    private String signup() throws Exception {
        String loginId = ("pur" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("loginId", loginId),
                                Map.entry("email", loginId + "@example.com"),
                                Map.entry("password", "StrongPassword1!"),
                                Map.entry("name", "매입테스트"),
                                Map.entry("birthDate", "1990-01-01"),
                                Map.entry("gender", "FEMALE"),
                                Map.entry("phone", "01012345678"),
                                Map.entry("postcode", "06236"),
                                Map.entry("address1", "서울 강남구"),
                                Map.entry("address2", "101호"),
                                Map.entry("termsAgreed", true),
                                Map.entry("privacyAgreed", true)))))
                .andReturn();
        String cookie = result.getResponse().getCookie("access_token") == null ? null
                : result.getResponse().getCookie("access_token").getValue();
        assertThat(cookie).as("signup should issue an access token: " + result.getResponse().getContentAsString())
                .isNotNull();
        return cookie;
    }

    private void cleanup() {
        jdbc.update("DELETE FROM ai_recommendation_history");
        jdbc.update("DELETE FROM product_cost_history");
        jdbc.update("DELETE FROM inventory_movement WHERE purchase_receipt_id IS NOT NULL");
        jdbc.update("DELETE FROM purchase_receipt_item");
        jdbc.update("DELETE FROM purchase_receipt");
        jdbc.update("DELETE FROM purchase_evidence");
        jdbc.update("DELETE FROM purchase_ledger_item");
        jdbc.update("DELETE FROM purchase_ledger");
        jdbc.update("DELETE FROM purchase_ledger_sequence");
        jdbc.update("DELETE FROM supplier_product_mapping");
        jdbc.update("DELETE FROM supplier");
        jdbc.update("DELETE FROM order_cancel_request");
        jdbc.update("DELETE FROM payment");
        jdbc.update("DELETE FROM order_item");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM sku_cost WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code LIKE 'IT-PUR-%')");
        jdbc.update("DELETE FROM inventory_movement WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code LIKE 'IT-PUR-%')");
        jdbc.update("DELETE FROM inventory WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code LIKE 'IT-PUR-%')");
        jdbc.update("DELETE FROM cart_item WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code LIKE 'IT-PUR-%')");
        jdbc.update("DELETE FROM product_price_history WHERE product_id IN (SELECT product_id FROM product WHERE product_name = ?)",
                PRODUCT_NAME);
        jdbc.update("DELETE FROM product_sku WHERE sku_code LIKE 'IT-PUR-%'");
        jdbc.update("DELETE FROM product_image WHERE product_id IN (SELECT product_id FROM product WHERE product_name = ?)",
                PRODUCT_NAME);
        jdbc.update("DELETE FROM product WHERE product_name = ?", PRODUCT_NAME);
    }
}
