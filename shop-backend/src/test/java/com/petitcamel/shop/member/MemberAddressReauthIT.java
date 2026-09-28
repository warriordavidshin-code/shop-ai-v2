package com.petitcamel.shop.member;

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
class MemberAddressReauthIT {

    private static final String PASSWORD = "StrongPassword1!";

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

    private Cookie accessCookie;
    private long memberId;

    @BeforeEach
    void setUp() throws Exception {
        String loginId = ("addr" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("loginId", loginId),
                                Map.entry("email", loginId + "@example.com"),
                                Map.entry("password", PASSWORD),
                                Map.entry("name", "배송지테스트"),
                                Map.entry("birthDate", "1990-01-01"),
                                Map.entry("gender", "FEMALE"),
                                Map.entry("phone", "01012345678"),
                                Map.entry("postcode", "30100"),
                                Map.entry("address1", "세종 한누리대로 1"),
                                Map.entry("address2", "101호"),
                                Map.entry("termsAgreed", true),
                                Map.entry("privacyAgreed", true)))))
                .andExpect(status().isCreated())
                .andReturn();
        accessCookie = result.getResponse().getCookie("access_token");
        assertThat(accessCookie).isNotNull();
        memberId = objectMapper.readTree(result.getResponse().getContentAsString()).get("memberId").asLong();
    }

    @Test
    void addressBookSeedsFromProfileAndKeepsSingleDefault() throws Exception {
        mockMvc.perform(get("/api/members/me/addresses").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].defaultAddress").value(true))
                .andExpect(jsonPath("$[0].postcode").value("30100"));

        MvcResult created = mockMvc.perform(post("/api/members/me/addresses")
                        .cookie(accessCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"회사","receiverName":"홍길동","receiverPhone":"010-9999-8888",
                                 "postcode":"06236","address1":"서울 강남구 테헤란로 1","address2":"3층",
                                 "defaultAddress":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true))
                .andReturn();
        long companyId = objectMapper.readTree(created.getResponse().getContentAsString()).get("addressId").asLong();

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/members/me/addresses").cookie(accessCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("addressId").asLong()).isEqualTo(companyId);
        assertThat(list.get(1).get("defaultAddress").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT postcode FROM member WHERE member_id = ?", String.class, memberId)).isEqualTo("06236");

        long seededId = list.get(1).get("addressId").asLong();
        mockMvc.perform(post("/api/members/me/addresses/{id}/default", seededId).cookie(accessCookie).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress").value(true));

        mockMvc.perform(delete("/api/members/me/addresses/{id}", seededId).cookie(accessCookie).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me/addresses").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].addressId").value(companyId))
                .andExpect(jsonPath("$[0].defaultAddress").value(true));

        Integer defaults = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_address WHERE member_id = ? AND is_default", Integer.class, memberId);
        assertThat(defaults).isEqualTo(1);
    }

    @Test
    void profileUpdateRequiresPasswordReauthentication() throws Exception {
        String body = "{\"name\":\"변경된이름\"}";

        mockMvc.perform(patch("/api/members/me")
                        .cookie(accessCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));

        mockMvc.perform(get("/api/members/me/reauth").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.method").value("LOCAL"));

        mockMvc.perform(post("/api/members/me/reauth")
                        .cookie(accessCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"WrongPassword1!\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult verified = mockMvc.perform(post("/api/members/me/reauth")
                        .cookie(accessCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andReturn();
        Cookie reauthCookie = verified.getResponse().getCookie("profile_reauth");
        assertThat(reauthCookie).isNotNull();
        assertThat(reauthCookie.isHttpOnly()).isTrue();

        mockMvc.perform(get("/api/members/me/reauth").cookie(accessCookie, reauthCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(patch("/api/members/me")
                        .cookie(accessCookie, reauthCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("변경된이름"));

        mockMvc.perform(get("/api/members/me").cookie(reauthCookie))
                .andExpect(status().isUnauthorized());
    }
}
