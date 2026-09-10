package com.petitcamel.shop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.auth.repository.RefreshTokenRepository;
import com.petitcamel.shop.auth.service.RefreshTokenService;
import com.petitcamel.shop.member.domain.Gender;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.member.domain.MemberStatus;
import com.petitcamel.shop.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class AuthIntegrationIT {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-04T00:00:00Z");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/shop_ai"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("DB_USERNAME", "shop_ai"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("DB_PASSWORD", "shop_ai123!"));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(FIXED_INSTANT, SEOUL);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAuthData() {
        jdbcTemplate.update("UPDATE inventory_movement SET actor_member_id = NULL WHERE actor_member_id IS NOT NULL");
        jdbcTemplate.update("UPDATE audit_log SET actor_member_id = NULL WHERE actor_member_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM recommendation_event");
        jdbcTemplate.update("DELETE FROM ai_recommendation");
        jdbcTemplate.update("DELETE FROM review");
        jdbcTemplate.update("DELETE FROM wishlist");
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        jdbcTemplate.update("DELETE FROM payment");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    void signupSuccess() throws Exception {
        String email = uniqueEmail("signup");
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("signup"), email, "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.loginId").isString())
                .andExpect(jsonPath("$.age").value(36))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn();

        assertThat(extractCookie(result, "access_token")).isNotBlank();
        assertThat(extractCookie(result, "refresh_token")).isNotBlank();
    }

    @Test
    void duplicateEmailRejected() throws Exception {
        String email = uniqueEmail("dup");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("dup1"), email, "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("dup2"), email.toUpperCase(), "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void duplicateLoginIdRejected() throws Exception {
        String loginId = uniqueLoginId("sameid");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(loginId, uniqueEmail("id1"), "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(loginId.toUpperCase(), uniqueEmail("id2"), "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void weakPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("weak"), uniqueEmail("weak"), "password", "1990-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void under14Rejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("child"), uniqueEmail("child"), "StrongPassword1!", "2015-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("14세")));
    }

    @Test
    void loginSuccessAndFailure() throws Exception {
        String loginId = uniqueLoginId("login");
        String email = uniqueEmail("login");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(loginId, email, "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"StrongPassword1!"}
                                """.formatted(loginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(loginId))
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"WrongPassword1!"}
                                """.formatted(loginId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"missing%s","password":"StrongPassword1!"}
                                """.formatted(loginId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void blockedAccountCannotLogin() throws Exception {
        String email = uniqueEmail("blocked");
        Member member = new Member();
        member.setLoginId(uniqueLoginId("blocked"));
        member.setEmail(email);
        member.setPasswordHash(passwordEncoder.encode("StrongPassword1!"));
        member.setName("차단회원");
        member.setBirthDate(LocalDate.of(1990, 1, 1));
        member.setGender(Gender.FEMALE);
        member.setPhone("01012345678");
        member.setPostcode("30100");
        member.setAddress1("세종");
        member.setRole(MemberRole.CUSTOMER);
        member.setStatus(MemberStatus.BLOCKED);
        Instant now = FIXED_INSTANT;
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberRepository.save(member);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"StrongPassword1!"}
                                """.formatted(member.getLoginId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ageUsesFixedClockBean() throws Exception {
        String email = uniqueEmail("age");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("age"), email, "StrongPassword1!", "2000-09-04")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.age").value(26));
    }

    @Test
    void refreshRotatesToken() throws Exception {
        String email = uniqueEmail("refresh");
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("refresh"), email, "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated())
                .andReturn();

        String oldRefresh = extractCookie(signup, "refresh_token");
        String oldHash = RefreshTokenService.hash(oldRefresh);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh)))
                .andExpect(status().isOk())
                .andReturn();

        String newRefresh = extractCookie(refreshed, "refresh_token");
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);

        assertThat(refreshTokenRepository.findByTokenHash(oldHash))
                .isPresent()
                .get()
                .extracting(token -> token.getRevokedAt())
                .isNotNull();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String email = uniqueEmail("logout");
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("logout"), email, "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated())
                .andReturn();

        String refresh = extractCookie(signup, "refresh_token");
        String hash = RefreshTokenService.hash(refresh);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", refresh)))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findByTokenHash(hash))
                .isPresent()
                .get()
                .extracting(token -> token.getRevokedAt())
                .isNotNull();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessAdmin() throws Exception {
        String email = uniqueEmail("customer");
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("customer"), email, "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated())
                .andReturn();

        String access = extractCookie(signup, "access_token");

        mockMvc.perform(get("/api/admin/dashboard")
                        .cookie(new Cookie("access_token", access))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getMeRequiresAuthAndReturnsProfile() throws Exception {
        String email = uniqueEmail("me");
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueLoginId("me"), email, "StrongPassword1!", "1990-01-01")))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());

        String access = extractCookie(signup, "access_token");
        mockMvc.perform(get("/api/members/me")
                        .cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.age").value(36));
    }

    private String signupJson(String loginId, String email, String password, String birthDate) throws Exception {
        return objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("loginId", loginId),
                Map.entry("email", email),
                Map.entry("password", password),
                Map.entry("name", "홍길동"),
                Map.entry("birthDate", birthDate),
                Map.entry("gender", "FEMALE"),
                Map.entry("phone", "01012345678"),
                Map.entry("postcode", "30100"),
                Map.entry("address1", "세종특별자치시"),
                Map.entry("address2", "101동"),
                Map.entry("termsAgreed", true),
                Map.entry("privacyAgreed", true)
        ));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueLoginId(String prefix) {
        String base = prefix.replaceAll("[^A-Za-z0-9]", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "u" + base;
        }
        String id = (base + UUID.randomUUID().toString().replace("-", "")).toLowerCase();
        return id.substring(0, Math.min(20, id.length()));
    }

    private String extractCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("cookie %s", name).isNotNull();
        return cookie.getValue();
    }
}
