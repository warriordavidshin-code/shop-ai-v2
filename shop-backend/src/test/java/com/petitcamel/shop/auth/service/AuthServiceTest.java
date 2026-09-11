package com.petitcamel.shop.auth.service;

import com.petitcamel.shop.auth.dto.LoginRequest;
import com.petitcamel.shop.auth.dto.SignupRequest;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Gender;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.member.domain.MemberStatus;
import com.petitcamel.shop.member.dto.MemberResponse;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.member.service.MemberService;
import com.petitcamel.shop.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    MemberRepository memberRepository;
    @Mock
    MemberService memberService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtService jwtService;
    @Mock
    RefreshTokenService refreshTokenService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                memberRepository, memberService, passwordEncoder, jwtService, refreshTokenService, CLOCK);
    }

    @Test
    void signupNormalizesLoginIdAndIssuesTokens() {
        SignupRequest request = new SignupRequest(
                "CamelUser",
                "User@Example.COM",
                "StrongPassword1!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                Gender.FEMALE,
                "01012345678",
                "30100",
                "세종",
                null,
                true,
                true);

        when(memberRepository.existsByLoginId("cameluser")).thenReturn(false);
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPassword1!")).thenReturn("hashed");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            m.setMemberId(10L);
            return m;
        });
        when(jwtService.createAccessToken(eq(10L), eq("cameluser"), eq(MemberRole.CUSTOMER)))
                .thenReturn("access");
        when(refreshTokenService.issue(10L)).thenReturn("refresh");
        when(memberService.toResponse(any(Member.class))).thenReturn(
                new MemberResponse(10L, "cameluser", "user@example.com", "홍길동", LocalDate.of(1990, 1, 1),
                        36, Gender.FEMALE, "01012345678", "30100", "세종", null, MemberRole.CUSTOMER,
                        AuthProvider.LOCAL, null));

        AuthService.AuthResult result = authService.signup(request);

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getLoginId()).isEqualTo("cameluser");
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        verify(memberService).validateAgePolicy(LocalDate.of(1990, 1, 1));
    }

    @Test
    void loginUsesSameMessageWhenLoginIdMissing() {
        when(memberRepository.findByLoginId("missinguser")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missinguser", "pw")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(be.getMessage()).isEqualTo(AuthService.LOGIN_FAILURE_MESSAGE);
                });
        verify(refreshTokenService, never()).issue(anyLong());
    }

    @Test
    void loginBlocksWithdrawnAccount() {
        Member member = new Member();
        member.setMemberId(1L);
        member.setLoginId("cameluser");
        member.setEmail("user@example.com");
        member.setPasswordHash("hashed");
        member.setStatus(MemberStatus.WITHDRAWN);
        when(memberRepository.findByLoginId("cameluser")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("StrongPassword1!", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("cameluser", "StrongPassword1!")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
