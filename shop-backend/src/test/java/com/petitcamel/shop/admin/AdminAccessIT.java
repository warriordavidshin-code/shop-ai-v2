package com.petitcamel.shop.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_IT", matches = "true")
class AdminAccessIT {

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
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        // Shared local DB may retain FK refs (orders, movements) from other ITs.
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM refresh_token");
    }

    @Test
    void customerForbiddenOnAdminDashboard() throws Exception {
        String access = signupAndGetAccess("customer");

        mockMvc.perform(get("/api/admin/dashboard")
                        .cookie(new Cookie("access_token", access))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanAccessDashboard() throws Exception {
        String access = createAdminAndLogin("admin-dash");

        mockMvc.perform(get("/api/admin/dashboard")
                        .cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockCount").exists())
                .andExpect(jsonPath("$.onSaleProductCount").exists())
                .andExpect(jsonPath("$.recentOrderCount").exists())
                .andExpect(jsonPath("$.pendingPaymentCount").exists())
                .andExpect(jsonPath("$.memberCount").isNumber());
    }

    @Test
    void adminCanPatchMemberStatusAndCreatesAuditLog() throws Exception {
        String adminAccess = createAdminAndLogin("admin-member");

        Member target = new Member();
        Instant now = Instant.now();
        target.setEmail("target+" + UUID.randomUUID() + "@example.com");
        target.setPasswordHash(passwordEncoder.encode("StrongPassword1!"));
        target.setName("대상회원");
        target.setBirthDate(LocalDate.of(1990, 1, 1));
        target.setGender(Gender.FEMALE);
        target.setPhone("01099998888");
        target.setPostcode("30100");
        target.setAddress1("세종");
        target.setAddress2("101");
        target.setRole(MemberRole.CUSTOMER);
        target.setStatus(MemberStatus.ACTIVE);
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        target = memberRepository.save(target);

        mockMvc.perform(patch("/api/admin/members/{memberId}/status", target.getMemberId())
                        .cookie(new Cookie("access_token", adminAccess))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "BLOCKED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(target.getMemberId()))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM audit_log
                        WHERE action = 'MEMBER_STATUS_UPDATE'
                          AND target_type = 'MEMBER'
                          AND target_id = ?
                        """,
                Integer.class,
                String.valueOf(target.getMemberId()));
        assertThat(auditCount).isEqualTo(1);

        String detail = jdbcTemplate.queryForObject(
                """
                        SELECT detail FROM audit_log
                        WHERE action = 'MEMBER_STATUS_UPDATE' AND target_id = ?
                        """,
                String.class,
                String.valueOf(target.getMemberId()));
        assertThat(detail).contains("from=ACTIVE").contains("to=BLOCKED");
        assertThat(detail).doesNotContain("password").doesNotContain(target.getEmail());
    }

    private String createAdminAndLogin(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.com";
        String password = "StrongPassword1!";

        Instant now = Instant.now();
        Member admin = new Member();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setName("관리자");
        admin.setBirthDate(LocalDate.of(1988, 5, 5));
        admin.setGender(Gender.OTHER);
        admin.setPhone("01011112222");
        admin.setPostcode("30100");
        admin.setAddress1("세종");
        admin.setAddress2(null);
        admin.setRole(MemberRole.ADMIN);
        admin.setStatus(MemberStatus.ACTIVE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        memberRepository.save(admin);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("access_token");
        assertThat(cookie).as("access_token").isNotNull();
        return cookie.getValue();
    }

    private String signupAndGetAccess(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.com";
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("email", email),
                                Map.entry("password", "StrongPassword1!"),
                                Map.entry("name", "고객"),
                                Map.entry("birthDate", "1990-01-01"),
                                Map.entry("gender", "FEMALE"),
                                Map.entry("phone", "01012345678"),
                                Map.entry("postcode", "30100"),
                                Map.entry("address1", "세종특별자치시"),
                                Map.entry("address2", "101동"),
                                Map.entry("termsAgreed", true),
                                Map.entry("privacyAgreed", true)))))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie cookie = signup.getResponse().getCookie("access_token");
        assertThat(cookie).as("access_token").isNotNull();
        return cookie.getValue();
    }
}
