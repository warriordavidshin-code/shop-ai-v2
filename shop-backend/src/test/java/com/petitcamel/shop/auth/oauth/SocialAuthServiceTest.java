package com.petitcamel.shop.auth.oauth;

import com.petitcamel.shop.auth.service.AuthService;
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
import static org.mockito.ArgumentMatchers.any;
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
    void createsKakaoMemberWhenUnknownAndDoesNotLinkExistingEmail() {
        SocialProfile profile = new SocialProfile(
                AuthProvider.KAKAO,
                "12345",
                "taken@example.com",
                "카카오닉",
                "https://img.example/a.png",
                null,
                Gender.FEMALE);

        when(memberRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("taken@example.com")).thenReturn(true);
        when(memberRepository.existsByLoginId(any())).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            m.setMemberId(77L);
            return m;
        });
        when(authService.completeAuthenticatedSession(any(Member.class))).thenReturn(
                new AuthService.AuthResult(
                        new MemberResponse(77L, "kakao_abc", null, "카카오닉", null, null, Gender.FEMALE,
                                null, null, null, null, MemberRole.CUSTOMER, AuthProvider.KAKAO,
                                "https://img.example/a.png"),
                        "access",
                        "refresh"));

        AuthService.AuthResult result = socialAuthService.loginOrSignup(profile);

        assertThat(result.accessToken()).isEqualTo("access");
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(saved.getProviderUserId()).isEqualTo("12345");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getLoginId()).startsWith("kakao_");
    }

    @Test
    void reusesExistingNaverMember() {
        Member existing = new Member();
        existing.setMemberId(9L);
        existing.setLoginId("naver_user");
        existing.setAuthProvider(AuthProvider.NAVER);
        existing.setProviderUserId("nv-1");
        existing.setName("기존");

        SocialProfile profile = new SocialProfile(
                AuthProvider.NAVER,
                "nv-1",
                "a@example.com",
                "네이버",
                null,
                LocalDate.of(1995, 5, 5),
                Gender.MALE);

        when(memberRepository.findByAuthProviderAndProviderUserId(AuthProvider.NAVER, "nv-1"))
                .thenReturn(Optional.of(existing));
        when(authService.completeAuthenticatedSession(existing)).thenReturn(
                new AuthService.AuthResult(
                        new MemberResponse(9L, "naver_user", null, "기존", null, null, null,
                                null, null, null, null, MemberRole.CUSTOMER, AuthProvider.NAVER, null),
                        "access",
                        "refresh"));

        AuthService.AuthResult result = socialAuthService.loginOrSignup(profile);

        assertThat(result.refreshToken()).isEqualTo("refresh");
        verify(authService).completeAuthenticatedSession(existing);
    }
}
