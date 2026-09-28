package com.petitcamel.shop.member.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.member.dto.ReauthStatusResponse;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.security.AuthCookieService;
import com.petitcamel.shop.security.CookieProperties;
import com.petitcamel.shop.security.JwtProperties;
import com.petitcamel.shop.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileReauthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    JwtService jwtService;
    ProfileReauthService service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-that-is-long-enough-for-hmac-sha-256!!");
        jwtService = new JwtService(jwtProperties, CLOCK);
        AuthCookieService cookieService = new AuthCookieService(new CookieProperties(), jwtProperties);
        service = new ProfileReauthService(memberRepository, passwordEncoder, jwtService, cookieService, CLOCK);
    }

    @Test
    void correctPasswordIssuesReauthTokenAcceptedByRequireVerified() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(localMember(1L)));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        ProfileReauthService.ReauthToken token = service.verifyPassword(1L, "pw");

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(ProfileReauthService.REAUTH_TTL));
        MockHttpServletRequest request = requestWithReauth(token.token());
        service.requireVerified(request, 1L);

        ReauthStatusResponse status = service.status(request, 1L);
        assertThat(status.verified()).isTrue();
        assertThat(status.method()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    void reauthTokenOfAnotherMemberIsRejected() {
        String otherToken = jwtService.createReauthToken(2L, "LOCAL", 600);

        assertThatThrownBy(() -> service.requireVerified(requestWithReauth(otherToken), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REAUTH_REQUIRED);
    }

    @Test
    void missingCookieRequiresReauth() {
        assertThatThrownBy(() -> service.requireVerified(new MockHttpServletRequest(), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.REAUTH_REQUIRED);
    }

    @Test
    void accessTokenCannotBeUsedAsReauthTokenAndViceVersa() {
        String accessToken = jwtService.createAccessToken(1L, "user1", MemberRole.CUSTOMER);
        String reauthToken = jwtService.createReauthToken(1L, "LOCAL", 600);

        assertThatThrownBy(() -> service.requireVerified(requestWithReauth(accessToken), 1L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> jwtService.parseAccessToken(reauthToken))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void locksOutAfterRepeatedWrongPasswords() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(localMember(1L)));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        for (int i = 0; i < ProfileReauthService.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verifyPassword(1L, "wrong"))
                    .extracting("code")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }
        assertThatThrownBy(() -> service.verifyPassword(1L, "wrong"))
                .extracting("code")
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void socialMemberCannotUsePasswordVerification() {
        Member member = localMember(1L);
        member.setAuthProvider(AuthProvider.KAKAO);
        member.setPasswordHash(null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.verifyPassword(1L, "pw"))
                .extracting("code")
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void socialVerificationRequiresSameProviderAccount() {
        Member member = localMember(1L);
        member.setAuthProvider(AuthProvider.KAKAO);
        member.setProviderUserId("kakao-123");
        lenient().when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(service.verifySocial(1L, AuthProvider.KAKAO, "kakao-123").token()).isNotBlank();
        assertThatThrownBy(() -> service.verifySocial(1L, AuthProvider.KAKAO, "kakao-999"))
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.verifySocial(1L, AuthProvider.NAVER, "kakao-123"))
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static MockHttpServletRequest requestWithReauth(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieService.REAUTH_COOKIE, token));
        return request;
    }

    private static Member localMember(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        member.setAuthProvider(AuthProvider.LOCAL);
        member.setPasswordHash("hash");
        member.setName("홍길동");
        return member;
    }
}
