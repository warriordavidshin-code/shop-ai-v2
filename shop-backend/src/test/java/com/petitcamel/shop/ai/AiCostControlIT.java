package com.petitcamel.shop.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.ai.service.AiQuotaService;
import com.petitcamel.shop.ai.service.AiSettingsService;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.security.JwtService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 비용 최소화 (지시서 테스트 1~7). Gemini/OpenAI are local fake servers that count calls, so "paid API was not
 * called" is verified directly. Local OCR uses the real PaddleOCR sidecar when IT_OCR_URL is set, otherwise a fake
 * that replays a recorded PaddleOCR response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class AiCostControlIT {

    private static final HttpServer SERVER = startServer();
    private static final AtomicInteger GEMINI_CALLS = new AtomicInteger();
    private static final AtomicInteger OPENAI_CALLS = new AtomicInteger();
    private static final AtomicInteger OCR_CALLS = new AtomicInteger();
    private static volatile String geminiMode = "OK";
    private static volatile String lastGeminiBody = "";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/shop_ai"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_USERNAME", "shop_ai"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_PASSWORD", "shop_ai123!"));
        String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("app.ai.cost.gemini.api-key", () -> "it-fake-gemini-key");
        registry.add("app.ai.cost.gemini.base-url", () -> base + "/gemini");
        registry.add("app.ai.analysis.api-key", () -> "sk-it-fake-openai-key");
        registry.add("app.ai.analysis.base-url", () -> base + "/openai");
        registry.add("app.ai.cost.openai-enabled", () -> "true");
        registry.add("app.ai.cost.paid-fallback-enabled", () -> "false");
        registry.add("app.ai.cost.local-ocr-enabled", () -> "true");
        registry.add("app.ai.cost.ocr.url", () -> System.getenv().getOrDefault("IT_OCR_URL", base));
        registry.add("app.ai.cost.ocr.timeout-seconds", () -> "120");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;
    @Autowired AiSettingsService settingsService;
    @Autowired AiQuotaService quotaService;

    private long adminId;
    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        String token = signup();
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE member_id = ?", claims.memberId());
        adminId = claims.memberId();
        admin = new Cookie("access_token",
                jwtService.createAccessToken(claims.memberId(), claims.loginId(), MemberRole.ADMIN));
        geminiMode = "OK";
        GEMINI_CALLS.set(0);
        OPENAI_CALLS.set(0);
        OCR_CALLS.set(0);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    /** 1. OpenAI 키를 쓰지 않고 영수증 OCR(로컬 OCR + 규칙)로 초안을 만든다. 불확실하면 Gemini 보조, 실패하면 로컬 결과. */
    @Test
    void receiptIsReadByLocalOcrWithoutCallingOpenAi() throws Exception {
        byte[] png = fixture("/fixtures/receipt-sample.png");
        geminiMode = "FAIL";
        JsonNode local = receipt(png, false);
        assertThat(local.path("provider").asText()).isEqualTo("LOCAL_OCR");
        assertThat(local.path("billingType").asText()).isEqualTo("LOCAL");
        assertThat(local.path("mock").asBoolean()).isFalse();
        assertThat(local.path("draft").path("supplierName").asText()).isEqualTo("테스트상사");
        assertThat(local.path("draft").path("items")).hasSize(2);
        assertThat(local.path("paidApprovalAvailable").asBoolean()).isTrue();
        assertThat(local.path("notices").toString()).contains("Gemini");
        assertThat(OPENAI_CALLS.get()).isZero();

        geminiMode = "OK";
        JsonNode assisted = receipt(png, true);
        assertThat(assisted.path("provider").asText()).isEqualTo("GEMINI");
        assertThat(assisted.path("billingType").asText()).isEqualTo("FREE");
        assertThat(lastGeminiBody).contains("테스트상사");
        assertThat(OPENAI_CALLS.get()).isZero();
        assertThat(count("SELECT COUNT(*) FROM ai_analysis_history WHERE provider = 'OPENAI'")).isZero();
    }

    /** 2. Gemini 무료 상품 분석 + 3. 같은 이미지 재분석 시 캐시 재사용(외부 호출 없음). */
    @Test
    void productAnalysisUsesGeminiFreeTierAndReusesCache() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        JsonNode first = product(png, false, 200);
        assertThat(first.path("provider").asText()).isEqualTo("GEMINI");
        assertThat(first.path("billingType").asText()).isEqualTo("FREE");
        assertThat(first.path("cached").asBoolean()).isFalse();
        assertThat(first.path("productNameCandidates").get(0).asText()).isEqualTo("린넨 블렌드 셔츠");
        assertThat(first.path("estimatedCostUsd").decimalValue()).isEqualByComparingTo("0");
        assertThat(GEMINI_CALLS.get()).isEqualTo(1);

        JsonNode second = product(png, false, 200);
        assertThat(second.path("cached").asBoolean()).isTrue();
        assertThat(second.path("analysisId").asLong()).isEqualTo(first.path("analysisId").asLong());
        assertThat(GEMINI_CALLS.get()).isEqualTo(1);
        assertThat(OPENAI_CALLS.get()).isZero();

        JsonNode usage = getJson("/api/admin/ai/cost-usage");
        JsonNode gemini = provider(usage, "GEMINI");
        assertThat(gemini.path("todayCalls").asLong()).isEqualTo(1);
        assertThat(gemini.path("todayCacheHits").asLong()).isEqualTo(1);
    }

    /** 4. 무료 한도(Gemini 429) 초과 시 안내하고 유료 API 는 승인 없이 호출하지 않는다 + 일일 요청 한도 초과 안내. */
    @Test
    void freeLimitExceededIsReportedAndDailyLimitBlocks() throws Exception {
        geminiMode = "RATE_LIMIT";
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 4, 4, 4, 4};
        JsonNode error = product(png, false, 409);
        assertThat(error.toString()).contains("한도를 초과").contains("승인");
        assertThat(OPENAI_CALLS.get()).isZero();
        assertThat(quotaService.isQuotaExhaustedToday(com.petitcamel.shop.ai.domain.AiProviderType.GEMINI)).isTrue();

        int before = GEMINI_CALLS.get();
        product(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 4, 4, 4, 5}, false, 409);
        assertThat(GEMINI_CALLS.get()).as("exhausted free tier is skipped for the rest of the day").isEqualTo(before);
        JsonNode usage = getJson("/api/admin/ai/cost-usage");
        assertThat(provider(usage, "GEMINI").path("quotaExhaustedToday").asBoolean()).isTrue();
        assertThat(usage.path("alerts").toString()).contains("Gemini");

        jdbc.update("DELETE FROM ai_usage_daily");
        geminiMode = "OK";
        updateSettings(Map.of("dailyRequestLimit", 1));
        product(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7, 7, 1}, false, 200);
        JsonNode limited = product(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7, 7, 2}, false, 429);
        assertThat(limited.toString()).contains("요청 한도").contains("수동 입력");
        assertThat(getJson("/api/admin/ai/cost-usage").path("dailyLimitReached").asBoolean()).isTrue();
    }

    /** 5. 유료 사용이 꺼져 있으면 OpenAI 를 호출하지 않고, 관리자가 1회 승인할 때만 호출한다(예산 예약·정산). */
    @Test
    void openAiIsBlockedUntilAdminApproves() throws Exception {
        geminiMode = "FAIL";
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9, 9};
        JsonNode blocked = product(png, false, 409);
        assertThat(blocked.path("code").asText()).isEqualTo("AI_PAID_APPROVAL_REQUIRED");
        assertThat(OPENAI_CALLS.get()).isZero();

        updateSettings(Map.of("openaiEnabled", false));
        product(png, true, 502);
        assertThat(OPENAI_CALLS.get()).as("disabled OpenAI is never called, even with approval").isZero();

        updateSettings(Map.of("openaiEnabled", true));
        JsonNode paid = product(png, true, 200);
        assertThat(paid.path("provider").asText()).isEqualTo("OPENAI");
        assertThat(paid.path("billingType").asText()).isEqualTo("PAID");
        assertThat(paid.path("estimatedCostUsd").decimalValue()).isPositive();
        assertThat(OPENAI_CALLS.get()).isEqualTo(1);
        JsonNode budget = getJson("/api/admin/ai/cost-usage").path("budget");
        assertThat(budget.path("spentUsd").decimalValue()).isPositive();
        assertThat(budget.path("reservedUsd").decimalValue()).isEqualByComparingTo("0");

        updateSettings(Map.of("openaiEnabled", true, "monthlyBudgetUsd", 0));
        JsonNode overBudget = product(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9, 8},
                true, 429);
        assertThat(overBudget.toString()).contains("예산");
        assertThat(OPENAI_CALLS.get()).isEqualTo(1);
    }

    /** 6. AI 가 모두 실패해도 수동 상품 등록은 정상 동작한다. */
    @Test
    void manualProductRegistrationWorksWhileAiFails() throws Exception {
        geminiMode = "FAIL";
        updateSettings(Map.of("openaiEnabled", false));
        product(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 6, 6, 6, 6}, false, 502);

        Map<String, Object> request = Map.of("categoryId", 3, "productName", "AIT 수동 등록 셔츠", "brandName", "AIT",
                "normalPrice", 30000, "salePrice", 30000, "status", "ON_SALE",
                "skus", List.of(Map.of("skuCode", "AIT-MANUAL-1", "color", "블랙", "size", "FREE")));
        mockMvc.perform(post("/api/admin/products").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful());
        assertThat(count("SELECT COUNT(*) FROM product WHERE product_name = 'AIT 수동 등록 셔츠'")).isEqualTo(1);

        updateSettings(Map.of("mode", "MANUAL"));
        JsonNode manual = getJson("/api/admin/ai/analysis-status");
        assertThat(manual.path("mode").asText()).isEqualTo("MANUAL");
        int calls = GEMINI_CALLS.get();
        product(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 6, 6, 6, 7}, false, 503);
        assertThat(GEMINI_CALLS.get()).isEqualTo(calls);
    }

    /** 7. 관리자 AI 사용량·비용 화면: 제공업체별 호출/토큰/예상비용/캐시/무료 한도/유료 여부, 실제 청구와 분리. */
    @Test
    void adminCostScreenSeparatesEstimatesFromActualBills() throws Exception {
        product(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 3, 3, 3, 3}, false, 200);
        String month = getJson("/api/admin/ai/cost-usage").path("month").asText();
        mockMvc.perform(post("/api/admin/ai/billing").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("provider", "OPENAI", "billingMonth", month,
                                "amountUsd", 0.42, "memo", "OpenAI 청구서"))))
                .andExpect(status().isOk());

        JsonNode usage = getJson("/api/admin/ai/cost-usage");
        assertThat(usage.path("mode").asText()).isEqualTo("FREE_FIRST");
        assertThat(usage.path("paidFallbackEnabled").asBoolean()).isFalse();
        JsonNode gemini = provider(usage, "GEMINI");
        assertThat(gemini.path("model").asText()).isNotBlank();
        assertThat(gemini.path("billing").asText()).isEqualTo("FREE");
        assertThat(gemini.path("todayInputTokens").asLong()).isEqualTo(1200);
        assertThat(gemini.path("todayOutputTokens").asLong()).isEqualTo(300);
        assertThat(gemini.path("monthCalls").asLong()).isEqualTo(1);
        assertThat(provider(usage, "OPENAI").path("keyConfigured").asBoolean()).isTrue();
        assertThat(usage.path("monthActualBilledUsd").decimalValue()).isEqualByComparingTo("0.42");
        assertThat(usage.path("monthEstimatedCostUsd").decimalValue()).isEqualByComparingTo("0");
        assertThat(usage.path("billingRecords")).hasSize(1);
        assertThat(usage.toString()).doesNotContain("it-fake-gemini-key").doesNotContain("sk-it-fake-openai-key");

        JsonNode settings = getJson("/api/admin/ai/settings");
        assertThat(settings.toString()).doesNotContain("it-fake-gemini-key").doesNotContain("sk-it-fake-openai-key");

        Cookie customer = new Cookie("access_token", signup());
        mockMvc.perform(get("/api/admin/ai/cost-usage").cookie(customer)).andExpect(status().isForbidden());
    }

    /** 동시 요청에서도 일일 한도·월 예산을 넘지 않는다. */
    @Test
    void dailyLimitAndBudgetAreSafeUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> acquire = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                acquire.add(() -> quotaService.tryAcquireDaily(5));
            }
            long granted = 0;
            for (Future<Boolean> f : pool.invokeAll(acquire)) {
                granted += f.get() ? 1 : 0;
            }
            assertThat(granted).isEqualTo(5);
            assertThat(quotaService.dailyUsed()).isEqualTo(5);

            List<Callable<Boolean>> reserve = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                reserve.add(() -> quotaService.tryReserveBudget(new BigDecimal("0.30"), new BigDecimal("1.00")));
            }
            long reserved = 0;
            for (Future<Boolean> f : pool.invokeAll(reserve)) {
                reserved += f.get() ? 1 : 0;
            }
            assertThat(reserved).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode receipt(byte[] png, boolean forceRefresh) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/purchases/ai/analyze")
                        .file(new MockMultipartFile("files", "statement.png", "image/png", png))
                        .param("forceRefresh", String.valueOf(forceRefresh))
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode product(byte[] png, boolean allowPaid, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/products/ai/analyze")
                        .file(new MockMultipartFile("files", "p.png", "image/png", png))
                        .param("allowPaid", String.valueOf(allowPaid))
                        .cookie(admin).with(csrf()))
                .andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void updateSettings(Map<String, Object> values) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        values.forEach((k, v) -> body.set(k, objectMapper.valueToTree(v)));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/ai/settings")
                        .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk());
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).cookie(admin)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode provider(JsonNode usage, String name) {
        for (JsonNode p : usage.path("providers")) {
            if (name.equals(p.path("provider").asText())) {
                return p;
            }
        }
        throw new AssertionError("provider row missing: " + name);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private byte[] fixture(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }

    private String signup() throws Exception {
        String loginId = ("ait" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("loginId", loginId), Map.entry("email", loginId + "@example.com"),
                                Map.entry("password", "StrongPassword1!"), Map.entry("name", "비용테스트"),
                                Map.entry("birthDate", "1990-01-01"), Map.entry("gender", "FEMALE"),
                                Map.entry("phone", "01012345678"), Map.entry("postcode", "06236"),
                                Map.entry("address1", "서울"), Map.entry("address2", "101호"),
                                Map.entry("termsAgreed", true), Map.entry("privacyAgreed", true)))))
                .andReturn();
        return result.getResponse().getCookie("access_token").getValue();
    }

    private void cleanup() {
        settingsService.update(new AiSettingsService.Overrides(null, null, null, null, null, null, null, null),
                adminId == 0 ? null : adminId);
        jdbc.update("DELETE FROM ai_usage_daily");
        jdbc.update("DELETE FROM ai_budget_month");
        jdbc.update("DELETE FROM ai_billing_record");
        jdbc.update("DELETE FROM product_ai_features WHERE ai_analysis_id IS NOT NULL");
        jdbc.update("DELETE FROM ai_recommendation_history");
        jdbc.update("DELETE FROM purchase_evidence");
        jdbc.update("DELETE FROM ai_analysis_history");
        jdbc.update("DELETE FROM inventory WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code LIKE 'AIT-%')");
        jdbc.update("DELETE FROM product_sku WHERE sku_code LIKE 'AIT-%'");
        jdbc.update("DELETE FROM product_price_history WHERE product_id IN (SELECT product_id FROM product WHERE brand_name = 'AIT')");
        jdbc.update("DELETE FROM product WHERE brand_name = 'AIT'");
    }

    // ---------------------------------------------------------------- fake providers

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/gemini", AiCostControlIT::gemini);
            server.createContext("/openai", AiCostControlIT::openai);
            server.createContext("/ocr", AiCostControlIT::ocr);
            server.createContext("/health", ex -> send(ex, 200, "{\"status\":\"UP\"}"));
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void gemini(HttpExchange ex) throws IOException {
        GEMINI_CALLS.incrementAndGet();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastGeminiBody = body;
        if (!"it-fake-gemini-key".equals(ex.getRequestHeaders().getFirst("x-goog-api-key"))) {
            send(ex, 401, "{\"error\":{\"message\":\"bad key\"}}");
            return;
        }
        switch (geminiMode) {
            case "RATE_LIMIT" -> send(ex, 429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");
            case "FAIL" -> send(ex, 500, "{\"error\":{\"code\":500,\"status\":\"INTERNAL\"}}");
            default -> {
                String output = body.contains("supplier_name") ? RECEIPT_OUTPUT : PRODUCT_OUTPUT;
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();
                ObjectNode candidate = root.putArray("candidates").addObject();
                candidate.putObject("content").putArray("parts").addObject().put("text", output);
                candidate.put("finishReason", "STOP");
                root.putObject("usageMetadata").put("promptTokenCount", 1200).put("candidatesTokenCount", 300);
                root.put("modelVersion", "gemini-2.5-flash");
                send(ex, 200, root.toString());
            }
        }
    }

    private static void openai(HttpExchange ex) throws IOException {
        OPENAI_CALLS.incrementAndGet();
        ex.getRequestBody().readAllBytes();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("id", "resp_it").put("status", "completed").put("model", "gpt-4.1-mini");
        root.putArray("output").addObject().put("type", "message").putArray("content").addObject()
                .put("type", "output_text").put("text", PRODUCT_OUTPUT);
        root.putObject("usage").put("input_tokens", 1500).put("output_tokens", 400);
        send(ex, 200, root.toString());
    }

    private static void ocr(HttpExchange ex) throws IOException {
        OCR_CALLS.incrementAndGet();
        ex.getRequestBody().readAllBytes();
        try (InputStream in = AiCostControlIT.class.getResourceAsStream("/fixtures/receipt-sample.ocr.json")) {
            send(ex, 200, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static final String PRODUCT_OUTPUT = """
            {"product_name_candidates":["린넨 블렌드 셔츠","데일리 린넨 셔츠"],"category_id":3,
             "category_reason":"셔츠 형태","summary":"가벼운 린넨 셔츠","description":"린넨 블렌드 셔츠입니다.",
             "colors":["화이트"],"style":"캐주얼","season":"봄","material_estimate":"린넨 추정","fit":"레귤러",
             "thickness":null,"stretch":null,"see_through":null,"keywords":["린넨","셔츠"],
             "seo_description":"린넨 셔츠","alt_texts":[{"image_index":0,"alt_text":"화이트 린넨 셔츠"}],
             "confidence":0.8,"needs_confirmation":["소재"],"warnings":[],"item_type":"셔츠",
             "category_group":"TOP","color_tone":"NEUTRAL","style_tags":["캐주얼"],"design_tags":["카라"]}
            """;

    private static final String RECEIPT_OUTPUT = """
            {"documents":[{"file_index":0,"document_type":"STATEMENT","confidence":0.9}],
             "supplier_name":"테스트상사","supplier_business_no":"124-81-00998","purchase_date":"2026-11-03",
             "items":[{"product_name":"니트","color":"블랙","size":"FREE","supplier_product_code":null,"quantity":10,
                       "unit_price":15000,"amount":150000,"confidence":0.9},
                      {"product_name":"슬랙스","color":"베이지","size":"M","supplier_product_code":null,"quantity":5,
                       "unit_price":20000,"amount":100000,"confidence":0.9}],
             "supply_amount":null,"tax_amount":null,"purchase_amount":250000,"payment_amount":null,
             "payment_date":null,"payment_method":null,"recipient_name":null,"transfer_reference":null,
             "evidence_type":null,
             "field_confidence":{"supplier_name":0.9,"purchase_date":0.9,"purchase_amount":0.9,
                                 "payment_amount":0.0,"items":0.9},
             "review_reasons":[],"warnings":[]}
            """;
}
