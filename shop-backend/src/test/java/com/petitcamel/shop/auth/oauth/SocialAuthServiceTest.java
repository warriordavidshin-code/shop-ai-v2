package com.petitcamel.shop.auth.oauth;

import com.petitcamel.shop.auth.service.AuthService;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Gender;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.member.dto.MemberResponse;
import com.petitcamel.shop.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    MemberRepository memberRepository;
    @Mock
    AuthService authService;

    SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        socialAuthService = new SocialAuthService(memberRepository, authService, CLOCK);
    }

    @Test
    void createsKakaoMemberWithConsentProfileFields() {
        SocialProfile profile = new SocialProfile(
                AuthProvider.KAKAO,
                "12345",
                "user@example.com",
                "홍길동",
                "https://img.example/a.png",
                LocalDate.of(1990, 1, 1),
                Gender.FEMALE,
                "01012345678",
                "30~39",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101동 1001호");

        when(memberRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(memberRepository.existsByLoginId(any())).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            m.setMemberId(77L);
            return m;
        });
        when(authService.completeAuthenticatedSession(any(Member.class))).thenReturn(
                new AuthService.AuthResult(
                        new MemberResponse(77L, "kakao_abc", "user@example.com", "홍길동", LocalDate.of(1990, 1, 1),
                                36, Gender.FEMALE, "01012345678", "06236", "서울특별시 강남구 테헤란로 123", "101동 1001호",
                                MemberRole.CUSTOMER, AuthProvider.KAKAO, "https://img.example/a.png"),
                        "access",
                        "refresh"));

        SocialAuthService.SocialAuthOutcome outcome = socialAuthService.loginOrSignup(profile);

        assertThat(outcome.newlyRegistered()).isTrue();
        assertThat(outcome.authResult().accessToken()).isEqualTo("access");
        assertThat(outcome.authResult().refreshToken()).isEqualTo("refresh");
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        verify(authService).completeAuthenticatedSession(captor.getValue());
        Member saved = captor.getValue();
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(saved.getProviderUserId()).isEqualTo("12345");
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(saved.getPhone()).isEqualTo("01012345678");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(saved.getPostcode()).isEqualTo("06236");
        assertThat(saved.getAddress1()).isEqualTo("서울특별시 강남구 테헤란로 123");
        assertThat(saved.getAddress2()).isEqualTo("101동 1001호");
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getLoginId()).startsWith("kakao_");
    }

    @Test
    void rejectsKakaoWhenEmailAlreadyRegistered() {
        SocialProfile profile = new SocialProfile(
                AuthProvider.KAKAO,
                "12345",
                "taken@example.com",
                "홍길동",
                null,
                LocalDate.of(1990, 1, 1),
                Gender.FEMALE,
                "01012345678",
                "30~39",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                null);

        when(memberRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> socialAuthService.loginOrSignup(profile))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void reusesExistingNaverMember() {
        Member existing = new Member();
        existing.setMemberId(9L);
        existing.setLoginId("naver_user");
        existing.setAuthProvider(AuthProvider.NAVER);
        existing.setProviderUserId("nv-1");
        existing.setName("기존");
        existing.setEmail("a@example.com");
        existing.setGender(Gender.MALE);
        existing.setBirthDate(LocalDate.of(1995, 5, 5));

        SocialProfile profile = new SocialProfile(
                AuthProvider.NAVER,
                "nv-1",
                "a@example.com",
                "네이버",
                null,
                LocalDate.of(1995, 5, 5),
                Gender.MALE,
                null,
                null,
                null,
                null,
                null);

        when(memberRepository.findByAuthProviderAndProviderUserId(AuthProvider.NAVER, "nv-1"))
                .thenReturn(Optional.of(existing));
        when(authService.completeAuthenticatedSession(existing)).thenReturn(
                new AuthService.AuthResult(
                        new MemberResponse(9L, "naver_user", null, "기존", null, null, null,
                                null, null, null, null, MemberRole.CUSTOMER, AuthProvider.NAVER, null),
                        "access",
                        "refresh"));

        SocialAuthService.SocialAuthOutcome outcome = socialAuthService.loginOrSignup(profile);

        assertThat(outcome.newlyRegistered()).isFalse();
        assertThat(outcome.authResult().refreshToken()).isEqualTo("refresh");
        verify(authService).completeAuthenticatedSession(existing);
        verify(memberRepository, never()).save(any());
    }
}
