package com.petitcamel.shop.reco;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.reco.service.CatalogSnapshotService;
import com.petitcamel.shop.reco.service.PopularityService;
import com.petitcamel.shop.reco.service.RecoSettingsService;
import com.petitcamel.shop.reco.service.RecoStatsService;
import com.petitcamel.shop.reco.service.RecommendationService;
import com.petitcamel.shop.security.JwtService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 상품 추천·개인화 쇼핑 (지시서 테스트 1~15) against a throwaway PostgreSQL. No AI provider is called.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class RecommendationIT {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0) RecommendationIT";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/shop_ai"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_USERNAME", "shop_ai"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_PASSWORD", "shop_ai123!"));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;
    @Autowired RecommendationService recommendationService;
    @Autowired PopularityService popularityService;
    @Autowired RecoSettingsService settingsService;
    @Autowired RecoStatsService statsService;
    @Autowired CatalogSnapshotService catalog;

    private long adminId;
    private String adminToken;
    private long memberId;
    private String memberToken;
    private RecoSettingsService.Settings originalSettings;
    private long aiCallsBefore;

    private long knitBlack;
    private long knitIvory;
    private long blouse;
    private long slacks;
    private long skirt;
    private long dress;
    private long cardigan;
    private long soldOut;
    private long hidden;
    private long discontinued;
    private long slowKnit;
    private final Map<Long, Long> skuOf = new java.util.HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        String admin = signup();
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(admin);
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE member_id = ?", claims.memberId());
        adminId = claims.memberId();
        adminToken = jwtService.createAccessToken(claims.memberId(), claims.loginId(), MemberRole.ADMIN);
        memberToken = signup();
        memberId = jwtService.parseAccessToken(memberToken).memberId();

        knitBlack = product("RIT 블랙 니트", 3, 39000, "ON_SALE", 10, "블랙", 30, 15000);
        knitIvory = product("RIT 아이보리 니트", 3, 42000, "ON_SALE", 10, "아이보리", 30, 16000);
        blouse = product("RIT 화이트 블라우스", 3, 45000, "ON_SALE", 10, "화이트", 30, 17000);
        slacks = product("RIT 블랙 슬랙스", 5, 49000, "ON_SALE", 10, "블랙", 30, 19000);
        skirt = product("RIT 베이지 스커트", 5, 39000, "ON_SALE", 10, "베이지", 30, 15000);
        dress = product("RIT 플라워 원피스", 4, 59000, "ON_SALE", 10, "핑크", 30, 22000);
        cardigan = product("RIT 신상 가디건", 3, 52000, "ON_SALE", 10, "그레이", 0, 20000);
        soldOut = product("RIT 품절 니트", 3, 40000, "ON_SALE", 0, "블랙", 30, 15000);
        hidden = product("RIT 판매중지 니트", 3, 40000, "HIDDEN", 10, "블랙", 30, 15000);
        discontinued = product("RIT 삭제 니트", 3, 40000, "DISCONTINUED", 10, "블랙", 30, 15000);
        slowKnit = product("RIT 장기재고 니트", 3, 40000, "ON_SALE", 40, "네이비", 120, 20000);

        originalSettings = settingsService.settings();
        recommendationService.clearCache();
        settingsService.invalidate();
        aiCallsBefore = count("SELECT COUNT(*) FROM ai_analysis_history");
    }

    @AfterEach
    void tearDown() {
        // 15. 추천 기능은 AI 제공업체를 호출하지 않는다 (모든 테스트 공통 검증)
        assertThat(count("SELECT COUNT(*) FROM ai_analysis_history")).isEqualTo(aiCallsBefore);
        if (originalSettings != null) {
            settingsService.update(originalSettings, adminId);
        }
        cleanup();
        recommendationService.clearCache();
    }

    /** 1. 회원 조회·찜 이력 기반 개인화 추천 + 3. 품절·판매중지·삭제 상품 제외 + 점수 근거 미리보기. */
    @Test
    void personalizedRecommendationsUseHistoryAndExcludeUnavailableProducts() throws Exception {
        Cookie member = new Cookie("access_token", memberToken);
        assertThat(postJson("/api/recommendations/track/view", Map.of("productId", knitBlack), member)
                .path("recorded").asBoolean()).isTrue();
        assertThat(postJson("/api/recommendations/track/view", Map.of("productId", blouse), member)
                .path("recorded").asBoolean()).isTrue();
        jdbc.update("INSERT INTO wishlist (member_id, product_id) VALUES (?, ?)", memberId, knitIvory);
        recommendationService.clearCache();

        JsonNode list = getJson("/api/recommendations/personalized?limit=30", member);
        assertThat(list.path("algorithm").asText()).isEqualTo("PERSONALIZED");
        assertThat(list.path("requestId").asText()).startsWith("v1.HOME_PERSONAL.");
        List<Long> ids = ids(list);
        assertThat(ids).isNotEmpty().doesNotContain(soldOut, hidden, discontinued);
        assertThat(ids).containsAnyOf(knitIvory, cardigan, slowKnit, blouse, knitBlack);
        list.path("items").forEach(item -> assertThat(item.path("reason").asText()).isNotBlank());

        JsonNode preview = getJson("/api/admin/recommendations/preview/personal?memberId=" + memberId,
                new Cookie("access_token", adminToken));
        assertThat(preview.path("items")).isNotEmpty();
        JsonNode breakdown = preview.path("items").get(0).path("breakdown");
        for (String key : List.of("categoryMatch", "colorMatch", "priceMatch", "purchaseSimilarity", "popularity",
                "freshness")) {
            assertThat(breakdown.has(key)).as(key).isTrue();
        }
    }

    /** 2. 비회원은 세션 기반, 신규 회원은 인기+신상품. */
    @Test
    void guestsGetSessionRecommendationsAndNewCustomersGetPopularPlusNew() throws Exception {
        String freshToken = signup();
        JsonNode fresh = getJson("/api/recommendations/personalized", new Cookie("access_token", freshToken));
        assertThat(fresh.path("algorithm").asText()).isEqualTo("POPULAR_AND_NEW");

        MvcResult first = mockMvc.perform(get("/api/recommendations/personalized").header("User-Agent", UA))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(first.getResponse().getContentAsString()).path("algorithm").asText())
                .isEqualTo("POPULAR_AND_NEW");
        Cookie session = first.getResponse().getCookie("rec_sid");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();

        assertThat(postJson("/api/recommendations/track/view", Map.of("productId", knitBlack), session)
                .path("recorded").asBoolean()).isTrue();
        recommendationService.clearCache();
        JsonNode guest = getJson("/api/recommendations/personalized", session);
        assertThat(guest.path("algorithm").asText()).isEqualTo("PERSONALIZED");
        assertThat(ids(guest)).doesNotContain(soldOut, hidden, discontinued);
    }

    /** 4. 상품 상세 "이 상품과 비슷한 상품" + 5. 코디 규칙(니트→슬랙스 우선, 같은 대분류 제외). */
    @Test
    void similarAndCoordinationRecommendations() throws Exception {
        JsonNode similar = getJson("/api/recommendations/similar/" + knitBlack + "?limit=20");
        assertThat(similar.path("title").asText()).isEqualTo("이 상품과 비슷한 상품");
        assertThat(ids(similar)).contains(knitIvory).doesNotContain(knitBlack, soldOut, hidden, discontinued);

        JsonNode coordi = getJson("/api/recommendations/coordination/" + knitBlack + "?limit=20");
        List<Long> coordiIds = ids(coordi);
        assertThat(coordiIds).contains(slacks, skirt).doesNotContain(knitIvory, blouse, knitBlack);
        assertThat(coordiIds.indexOf(slacks)).isLessThan(coordiIds.indexOf(skirt));

        JsonNode preview = getJson("/api/admin/recommendations/preview/coordination/" + knitBlack,
                new Cookie("access_token", adminToken));
        JsonNode top = preview.path("items").get(0);
        assertThat(top.path("breakdown").path("categoryRule").asDouble()).isGreaterThan(0);

        JsonNode price = getJson("/api/recommendations/similar-price/" + knitBlack);
        assertThat(ids(price)).doesNotContain(slacks, skirt, dress);
    }

    /** 6. 인기 상품 7/30일 집계, 봇·중복 조회 제외. */
    @Test
    void popularRankingCountsSalesAndFiltersBotsAndDuplicateViews() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        order(memberId, skirt, 3, 39000, 1);
        order(memberId, skirt, 2, 39000, 2);
        order(memberId, dress, 1, 59000, 20);

        Cookie member = new Cookie("access_token", memberToken);
        assertThat(postJson("/api/recommendations/track/view", Map.of("productId", dress), member, "Googlebot/2.1")
                .path("recorded").asBoolean()).isFalse();
        assertThat(postJson("/api/recommendations/track/view", Map.of("productId", dress), member)
                .path("recorded").asBoolean()).isTrue();
        assertThat(postJson("/api/recommendations/track/view", Map.of("productId", dress), member)
                .path("recorded").asBoolean()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM product_view_history WHERE product_id = " + dress)).isEqualTo(1);

        catalog.invalidate();
        popularityService.refreshAll();
        recommendationService.clearCache();
        List<Long> week = ids(getJson("/api/recommendations/popular?type=TOP10&period=7&limit=30"));
        assertThat(week).contains(skirt);
        if (week.contains(dress)) {
            assertThat(week.indexOf(skirt)).isLessThan(week.indexOf(dress));
        }
        List<Long> month = ids(getJson("/api/recommendations/popular?type=TOP10&period=30&limit=30"));
        assertThat(month).contains(skirt, dress);
        assertThat(today).isNotNull();

        List<Long> bottoms = ids(getJson("/api/recommendations/popular?type=TOP_BOTTOMS&period=30&limit=30"));
        assertThat(bottoms).contains(skirt).doesNotContain(dress, knitBlack);

        JsonNode adminPopular = getJson("/api/admin/recommendations/popular?type=TOP10&period=30",
                new Cookie("access_token", adminToken));
        assertThat(adminPopular.path("items")).isNotEmpty();
        assertThat(adminPopular.path("basis").asText()).contains("봇");
    }

    /** 7. 노출·클릭·장바구니 이벤트 중복 방지·서명 검증 + 8. 구매 기여와 성과 분석. */
    @Test
    void eventsAreDedupedSignedAndAttributedToPurchases() throws Exception {
        Cookie member = new Cookie("access_token", memberToken);
        JsonNode similar = getJson("/api/recommendations/similar/" + knitBlack, member);
        String requestId = similar.path("requestId").asText();
        assertThat(requestId).startsWith("v1.DETAIL_SIMILAR.");

        assertThat(event(member, requestId, "IMPRESSION", null)).isTrue();
        assertThat(event(member, requestId, "IMPRESSION", null)).isFalse();
        assertThat(event(member, requestId, "CLICK", knitIvory)).isTrue();
        assertThat(event(member, requestId, "CLICK", knitIvory)).isFalse();
        assertThat(event(member, requestId, "CART", knitIvory)).isTrue();
        String forged = requestId.substring(0, requestId.length() - 2) + (requestId.endsWith("AA") ? "BB" : "AA");
        assertThat(event(member, forged, "CLICK", knitIvory)).isFalse();
        assertThat(count("SELECT COUNT(*) FROM recommendation_event WHERE request_id = '" + requestId + "'"))
                .isEqualTo(3);

        long orderItemId = order(memberId, knitIvory, 1, 42000, 0);
        LocalDate today = LocalDate.now(ZONE);
        assertThat(statsService.attribute(today.minusDays(1), today)).isEqualTo(1);
        assertThat(statsService.attribute(today.minusDays(1), today)).isZero();
        assertThat(count("SELECT COUNT(*) FROM recommendation_event WHERE event_type = 'PURCHASE' AND order_item_id = "
                + orderItemId)).isEqualTo(1);

        JsonNode analytics = getJson("/api/admin/recommendations/analytics?days=7",
                new Cookie("access_token", adminToken));
        JsonNode row = null;
        for (JsonNode p : analytics.path("placements")) {
            if ("DETAIL_SIMILAR".equals(p.path("placement").asText())) {
                row = p;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.path("impressions").asLong()).isEqualTo(1);
        assertThat(row.path("clicks").asLong()).isEqualTo(1);
        assertThat(row.path("carts").asLong()).isEqualTo(1);
        assertThat(row.path("purchases").asLong()).isEqualTo(1);
        assertThat(row.path("revenue").decimalValue()).isEqualByComparingTo("42000");
        assertThat(row.path("ctr").asDouble()).isEqualTo(1.0);
        assertThat(analytics.path("totalRevenue").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("42000"));

        jdbc.update("UPDATE order_item SET status = 'CANCELLED' WHERE order_item_id = ?", orderItemId);
        JsonNode afterCancel = getJson("/api/admin/recommendations/analytics?days=7",
                new Cookie("access_token", adminToken));
        assertThat(afterCancel.path("recommendationRevenue").decimalValue()).isEqualByComparingTo("0");
    }

    /** 9. 매입 추천(계산식·사유) + 10. 재고 소진(최소 마진 준수) + 11. 승인해도 가격·발주 자동 변경 없음 + 14. 권한. */
    @Test
    void adminPurchaseAndSlowMovingRecommendationsNeverChangePricesOrOrders() throws Exception {
        for (int i = 0; i < 4; i++) {
            order(memberId, slacks, 2, 49000, i);
        }
        jdbc.update("UPDATE inventory SET stock_quantity = 2 WHERE sku_id = ?", skuOf.get(slacks));
        Cookie admin = new Cookie("access_token", adminToken);

        JsonNode purchase = getJson("/api/admin/recommendations/purchase?filter=ALL", admin);
        JsonNode slacksRow = find(purchase.path("rows"), slacks);
        assertThat(slacksRow).isNotNull();
        assertThat(slacksRow.path("decision").asText()).isEqualTo("REORDER");
        assertThat(slacksRow.path("sales7").asInt()).isEqualTo(8);
        assertThat(slacksRow.path("suggestedQuantity").asInt()).isPositive();
        assertThat(slacksRow.path("formula").asText()).contains("0.6×(8÷7)").contains("0.4×(8÷30)");
        assertThat(slacksRow.path("reasons")).isNotEmpty();
        assertThat(purchase.path("criteria").toString()).contains("자동 발주하지 않");

        JsonNode slow = getJson("/api/admin/recommendations/slow-moving", admin);
        JsonNode slowRow = find(slow.path("rows"), slowKnit);
        assertThat(slowRow).isNotNull();
        assertThat(slowRow.path("classes").toString()).contains("LONG_UNSOLD");
        assertThat(slowRow.path("strategies")).isNotEmpty();
        BigDecimal minMargin = slow.path("minimumMarginRate").decimalValue();
        BigDecimal maxDiscount = slow.path("maxDiscountRate").decimalValue();
        assertThat(slowRow.path("maxDiscountRate").decimalValue()).isLessThanOrEqualTo(maxDiscount);
        if (!slowRow.path("suggestedPrice").isNull()) {
            assertThat(slowRow.path("suggestedPrice").decimalValue())
                    .isGreaterThanOrEqualTo(slowRow.path("floorPrice").decimalValue())
                    .isLessThan(new BigDecimal("40000"));
            assertThat(slowRow.path("marginAfterDiscount").decimalValue()).isGreaterThanOrEqualTo(minMargin);
            assertThat(slowRow.path("classes").toString()).contains("DISCOUNT_REVIEW");
        }

        long purchaseOrdersBefore = count("SELECT COUNT(*) FROM purchase_ledger");
        mockMvc.perform(post("/api/admin/recommendations/decisions").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "SLOW_MOVING", "productId", slowKnit,
                                "decision", "ACCEPTED", "note", "시즌오프 할인 검토"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/recommendations/decisions").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "PURCHASE", "productId", slacks,
                                "skuId", skuOf.get(slacks), "decision", "ACCEPTED", "quantity", 10))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT sale_price FROM product WHERE product_id = ?", BigDecimal.class,
                slowKnit)).isEqualByComparingTo("40000");
        assertThat(count("SELECT COUNT(*) FROM purchase_ledger")).isEqualTo(purchaseOrdersBefore);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM inventory WHERE sku_id = ?", Integer.class,
                skuOf.get(slacks))).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ai_recommendation_history WHERE status = 'ACCEPTED'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action LIKE 'RECO_%_ACCEPTED' AND actor_member_id = "
                + adminId)).isEqualTo(2);
        assertThat(getJson("/api/admin/recommendations/decisions?type=SLOW_MOVING", admin)).hasSize(1);

        Cookie customer = new Cookie("access_token", memberToken);
        mockMvc.perform(get("/api/admin/recommendations/purchase").cookie(customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/recommendations/preview/personal?memberId=" + adminId).cookie(customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/recommendations/purchase")).andExpect(status().isUnauthorized());
    }

    /** 12. 관리자 설정(노출 영역·제외 상품·가중치)이 즉시 반영. */
    @Test
    void adminSettingsAndRulesApplyImmediately() throws Exception {
        Cookie admin = new Cookie("access_token", adminToken);
        ObjectNode settings = (ObjectNode) getJson("/api/admin/recommendations/settings", admin);
        settings.put("homeEnabled", false);
        settings.put("wCategory", 0.5);
        mockMvc.perform(put("/api/admin/recommendations/settings").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(settings.toString())).andExpect(status().isOk());
        assertThat(getJson("/api/recommendations/personalized").path("algorithm").asText()).isEqualTo("DISABLED");
        assertThat(getJson("/api/recommendations/popular").path("items")).isEmpty();
        assertThat(getJson("/api/recommendations/similar/" + knitBlack).path("algorithm").asText())
                .isEqualTo("SIMILAR");

        settings.put("wCategory", -1);
        mockMvc.perform(put("/api/admin/recommendations/settings").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(settings.toString()))
                .andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/admin/recommendations/rules").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ruleType", "EXCLUDE_PRODUCT",
                                "productId", knitIvory, "memo", "RIT"))))
                .andExpect(status().isOk()).andReturn();
        long ruleId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        assertThat(ids(getJson("/api/recommendations/similar/" + knitBlack + "?limit=30"))).doesNotContain(knitIvory);

        mockMvc.perform(delete("/api/admin/recommendations/rules/" + ruleId).cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        assertThat(ids(getJson("/api/recommendations/similar/" + knitBlack + "?limit=30"))).contains(knitIvory);
    }

    /** 13. 개인화 동의 해제 시 이력 삭제·추적 중단, 인기/신상품만 추천. */
    @Test
    void personalizationOptOutStopsProfiling() throws Exception {
        Cookie member = new Cookie("access_token", memberToken);
        postJson("/api/recommendations/track/view", Map.of("productId", knitBlack), member);
        postJson("/api/recommendations/track/search", Map.of("keyword", "블랙 니트"), member);
        assertThat(count("SELECT COUNT(*) FROM product_view_history WHERE member_id = " + memberId)).isEqualTo(1);
        assertThat(getJson("/api/members/me/personalization", member).path("enabled").asBoolean()).isTrue();

        mockMvc.perform(put("/api/members/me/personalization").cookie(member).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM product_view_history WHERE member_id = " + memberId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM customer_search_history WHERE member_id = " + memberId)).isZero();
        assertThat(getJson("/api/recommendations/personalized", member).path("algorithm").asText())
                .isEqualTo("POPULAR_AND_NEW");
        postJson("/api/recommendations/track/view", Map.of("productId", blouse), member);
        assertThat(postJson("/api/recommendations/track/search", Map.of("keyword", "블라우스"), member)
                .path("recorded").asBoolean()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM product_view_history WHERE member_id = " + memberId)).isZero();

        mockMvc.perform(get("/api/members/me/personalization")).andExpect(status().isUnauthorized());
    }

    /** 장바구니·마이페이지 영역 + 신상품 목록. */
    @Test
    void cartMypageAndNewArrivalPlacements() throws Exception {
        Cookie member = new Cookie("access_token", memberToken);
        Long cartId = jdbc.queryForObject("INSERT INTO cart (member_id) VALUES (?) RETURNING cart_id", Long.class,
                memberId);
        jdbc.update("INSERT INTO cart_item (cart_id, sku_id, quantity) VALUES (?, ?, 1)", cartId, skuOf.get(knitBlack));
        JsonNode cart = getJson("/api/recommendations/cart", member);
        assertThat(cart.path("placement").asText()).isEqualTo("CART_MATCH");
        assertThat(ids(cart)).contains(slacks).doesNotContain(knitBlack);

        JsonNode fresh = getJson("/api/recommendations/new-arrivals?limit=30");
        assertThat(ids(fresh)).contains(cardigan).doesNotContain(soldOut, hidden, discontinued);

        postJson("/api/recommendations/track/view", Map.of("productId", dress), member);
        recommendationService.clearCache();
        JsonNode recent = getJson("/api/recommendations/recent-similar", member);
        assertThat(recent.path("placement").asText()).isEqualTo("MYPAGE_RECENT");
        JsonNode taste = getJson("/api/recommendations/personalized?placement=MYPAGE_TASTE", member);
        assertThat(taste.path("placement").asText()).isEqualTo("MYPAGE_TASTE");
        cleanCart(cartId);
    }

    // ---------------------------------------------------------------- helpers

    private long product(String name, long categoryId, int price, String status, int stock, String color,
                         int createdDaysAgo, int purchasePrice) {
        Long id = jdbc.queryForObject("""
                INSERT INTO product (category_id, product_name, brand_name, normal_price, sale_price, status,
                                     purchase_price, created_at)
                VALUES (?, ?, 'RIT', ?, ?, ?, ?, NOW() - make_interval(days => ?)) RETURNING product_id""",
                Long.class, categoryId, name, price, price, status, purchasePrice, createdDaysAgo);
        Long sku = jdbc.queryForObject("""
                INSERT INTO product_sku (product_id, sku_code, color, size, status)
                VALUES (?, ?, ?, 'FREE', ?) RETURNING sku_id""", Long.class, id,
                "RIT-" + id, color, "DISCONTINUED".equals(status) ? "DISCONTINUED" : "ON_SALE");
        jdbc.update("INSERT INTO inventory (sku_id, stock_quantity, reorder_point) VALUES (?, ?, 3)", sku, stock);
        skuOf.put(id, sku);
        return id;
    }

    /** Paid order line {@code daysAgo} days ago; returns order_item_id. */
    private long order(long buyer, long productId, int qty, int unitPrice, int daysAgo) {
        String no = "RIT-" + UUID.randomUUID().toString().substring(0, 18);
        int amount = qty * unitPrice;
        Long orderId = jdbc.queryForObject("""
                INSERT INTO orders (order_no, member_id, order_status, total_product_amount, payment_amount,
                                    receiver_name, receiver_phone, postcode, address1, idempotency_key, ordered_at)
                VALUES (?, ?, 'PAID', ?, ?, '테스트', '01000000000', '00000', '테스트', ?,
                        NOW() - make_interval(days => ?)) RETURNING order_id""",
                Long.class, no, buyer, amount, amount, no, daysAgo);
        Long itemId = jdbc.queryForObject("""
                INSERT INTO order_item (order_id, product_id, sku_id, product_name, option_name, quantity, unit_price,
                                        payment_price)
                VALUES (?, ?, ?, 'RIT', 'FREE', ?, ?, ?) RETURNING order_item_id""",
                Long.class, orderId, productId, skuOf.get(productId), qty, unitPrice, amount);
        jdbc.update("""
                INSERT INTO payment (order_id, payment_amount, payment_status, approved_at)
                VALUES (?, ?, 'APPROVED', NOW() - make_interval(days => ?))""", orderId, amount, daysAgo);
        return itemId;
    }

    private boolean event(Cookie viewer, String requestId, String type, Long productId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("requestId", requestId);
        body.put("eventType", type);
        body.put("productId", productId);
        return postJson("/api/recommendations/events", body, viewer).path("recorded").asBoolean();
    }

    private JsonNode getJson(String url, Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get(url).header("User-Agent", UA);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String url, Object body, Cookie viewer) throws Exception {
        return postJson(url, body, viewer, UA);
    }

    private JsonNode postJson(String url, Object body, Cookie viewer, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post(url).header("User-Agent", userAgent).cookie(viewer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> ids = new ArrayList<>();
        list.path("items").forEach(item -> ids.add(item.path("product").path("productId").asLong()));
        return ids;
    }

    private static JsonNode find(JsonNode rows, long productId) {
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == productId) {
                return row;
            }
        }
        return null;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private String signup() throws Exception {
        String loginId = ("rit" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("loginId", loginId), Map.entry("email", loginId + "@example.com"),
                                Map.entry("password", "StrongPassword1!"), Map.entry("name", "추천테스트"),
                                Map.entry("birthDate", "1990-01-01"), Map.entry("gender", "FEMALE"),
                                Map.entry("phone", "01012345678"), Map.entry("postcode", "06236"),
                                Map.entry("address1", "서울"), Map.entry("address2", "101호"),
                                Map.entry("termsAgreed", true), Map.entry("privacyAgreed", true)))))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("access_token");
        assertThat(cookie).as(result.getResponse().getContentAsString()).isNotNull();
        return cookie.getValue();
    }

    private void cleanCart(Long cartId) {
        jdbc.update("DELETE FROM cart_item WHERE cart_id = ?", cartId);
        jdbc.update("DELETE FROM cart WHERE cart_id = ?", cartId);
    }

    private void cleanup() {
        String rit = "(SELECT product_id FROM product WHERE brand_name = 'RIT')";
        String ritOrders = "(SELECT order_id FROM orders WHERE order_no LIKE 'RIT-%')";
        jdbc.update("DELETE FROM recommendation_event WHERE request_id IS NOT NULL OR product_id IN " + rit);
        jdbc.update("DELETE FROM recommendation_daily_stats");
        jdbc.update("DELETE FROM product_recommendation");
        jdbc.update("DELETE FROM product_view_history WHERE product_id IN " + rit + " OR member_id IS NULL");
        jdbc.update("DELETE FROM customer_search_history");
        jdbc.update("DELETE FROM customer_product_preference");
        jdbc.update("DELETE FROM recommendation_rule WHERE memo = 'RIT'");
        jdbc.update("DELETE FROM ai_recommendation_history");
        jdbc.update("DELETE FROM payment WHERE order_id IN " + ritOrders);
        jdbc.update("DELETE FROM order_item WHERE order_id IN " + ritOrders);
        jdbc.update("DELETE FROM orders WHERE order_no LIKE 'RIT-%'");
        jdbc.update("DELETE FROM wishlist WHERE product_id IN " + rit);
        jdbc.update("DELETE FROM cart_item WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code LIKE 'RIT-%')");
        jdbc.update("DELETE FROM inventory WHERE sku_id IN (SELECT sku_id FROM product_sku WHERE sku_code LIKE 'RIT-%')");
        jdbc.update("DELETE FROM product_sku WHERE sku_code LIKE 'RIT-%'");
        jdbc.update("DELETE FROM product WHERE brand_name = 'RIT'");
        skuOf.clear();
    }
}
