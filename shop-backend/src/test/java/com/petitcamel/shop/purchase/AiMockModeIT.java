package com.petitcamel.shop.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.member.domain.MemberRole;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI flows in explicit mock mode (no API key): results are labelled mock, review-first, nothing auto-saved. */
@SpringBootTest(properties = "app.ai.analysis.mock-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class AiMockModeIT {

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

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        String loginId = ("aim" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("loginId", loginId), Map.entry("email", loginId + "@example.com"),
                                Map.entry("password", "StrongPassword1!"), Map.entry("name", "AI테스트"),
                                Map.entry("birthDate", "1990-01-01"), Map.entry("gender", "FEMALE"),
                                Map.entry("phone", "01012345678"), Map.entry("postcode", "30100"),
                                Map.entry("address1", "세종"), Map.entry("address2", "101"),
                                Map.entry("termsAgreed", true), Map.entry("privacyAgreed", true)))))
                .andReturn();
        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(
                result.getResponse().getCookie("access_token").getValue());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE member_id = ?", claims.memberId());
        adminToken = jwtService.createAccessToken(claims.memberId(), claims.loginId(), MemberRole.ADMIN);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void receiptAnalysisReturnsLabelledDraftAndSavesOnlyOnAdminSave() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6};
        byte[] pdf = "%PDF-1.4 mock transfer".getBytes();
        MvcResult analyzed = mockMvc.perform(multipart("/api/admin/purchases/ai/analyze")
                        .file(new MockMultipartFile("files", "statement.png", "image/png", png))
                        .file(new MockMultipartFile("files", "transfer.pdf", "application/pdf", pdf))
                        .cookie(new Cookie("access_token", adminToken)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(analyzed.getResponse().getContentAsString());
        assertThat(body.path("mock").asBoolean()).isTrue();
        assertThat(body.path("provider").asText()).isEqualTo("MOCK");
        assertThat(body.path("warnings").toString()).contains("MOCK");
        assertThat(body.path("documents")).hasSize(2);
        assertThat(body.path("draft").path("supplierName").asText()).startsWith("[MOCK]");
        assertThat(body.path("draft").path("items")).hasSize(1);
        assertThat(body.path("draft").path("statementKept").asText()).isEqualTo("KEPT");
        assertThat(body.path("draft").path("evidenceIds")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_ledger", Integer.class)).isZero();

        MvcResult cached = mockMvc.perform(multipart("/api/admin/purchases/ai/analyze")
                        .file(new MockMultipartFile("files", "statement.png", "image/png", png))
                        .file(new MockMultipartFile("files", "transfer.pdf", "application/pdf", pdf))
                        .cookie(new Cookie("access_token", adminToken)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        JsonNode second = objectMapper.readTree(cached.getResponse().getContentAsString());
        assertThat(second.path("cached").asBoolean()).isTrue();
        assertThat(second.path("draft").path("evidenceIds")).isEqualTo(body.path("draft").path("evidenceIds"));

        ObjectNode draft = (ObjectNode) body.path("draft");
        draft.put("supplierName", "검토완료 거래처");
        mockMvc.perform(post("/api/admin/purchases").cookie(new Cookie("access_token", adminToken)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(draft.toString()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_ledger", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_evidence WHERE purchase_ledger_id IS NOT NULL",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT entry_source FROM purchase_ledger", String.class)).isEqualTo("AI");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_history WHERE provider = 'MOCK' "
                + "AND COALESCE(estimated_cost_usd, 0) = 0", Integer.class)).isEqualTo(1);
    }

    @Test
    void productAnalysisAndRecommendationsAreMockLabelled() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 5, 5, 5, 5};
        MvcResult result = mockMvc.perform(multipart("/api/admin/products/ai/analyze")
                        .file(new MockMultipartFile("files", "p.png", "image/png", png))
                        .cookie(new Cookie("access_token", adminToken)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("mock").asBoolean()).isTrue();
        assertThat(body.path("productNameCandidates").get(0).asText()).startsWith("[MOCK]");
        assertThat(body.path("altTexts")).hasSize(1);

        mockMvc.perform(post("/api/admin/ai/purchase-recommendations")
                        .cookie(new Cookie("access_token", adminToken)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"useAi\":true}"))
                .andExpect(status().isOk());
    }

    private void cleanup() {
        jdbc.update("DELETE FROM ai_recommendation_history");
        jdbc.update("DELETE FROM purchase_evidence");
        jdbc.update("DELETE FROM purchase_ledger_item");
        jdbc.update("DELETE FROM purchase_ledger");
        jdbc.update("DELETE FROM purchase_ledger_sequence");
        jdbc.update("DELETE FROM supplier_product_mapping");
        jdbc.update("DELETE FROM supplier");
        jdbc.update("DELETE FROM ai_analysis_history");
    }
}
