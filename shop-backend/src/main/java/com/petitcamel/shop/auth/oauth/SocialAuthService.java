package com.petitcamel.shop.auth.oauth;

import com.petitcamel.shop.auth.service.AuthService;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Gender;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.member.domain.MemberStatus;
import com.petitcamel.shop.member.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class SocialAuthService {

    private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final AuthService authService;
    private final Clock clock;

    public SocialAuthService(MemberRepository memberRepository, AuthService authService, Clock clock) {
        this.memberRepository = memberRepository;
        this.authService = authService;
        this.clock = clock;
    }

    @Transactional
    public AuthService.AuthResult loginOrSignup(SocialProfile profile) {
        if (profile.provider() == null || profile.provider() == AuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 소셜 로그인입니다.");
        }
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "소셜 사용자 식별자를 확인할 수 없습니다.");
        }

        Optional<Member> existing = memberRepository.findByAuthProviderAndProviderUserId(
                profile.provider(), profile.providerUserId());
        Member member = existing.orElseGet(() -> createSocialMember(profile));
        if (existing.isPresent()) {
            refreshProfileHints(member, profile);
        }
        return authService.completeAuthenticatedSession(member);
    }

    private Member createSocialMember(SocialProfile profile) {
        Instant now = clock.instant();
        Member member = new Member();
        member.setLoginId(generateLoginId(profile.provider()));
        member.setEmail(resolveEmail(profile));
        member.setPasswordHash(null);
        member.setName(trimTo(profile.name() == null || profile.name().isBlank() ? defaultName(profile.provider()) : profile.name(), 100));
        member.setBirthDate(profile.birthDate());
        member.setGender(profile.gender() == null ? Gender.PREFER_NOT_TO_SAY : profile.gender());
        member.setPhone(null);
        member.setPostcode(null);
        member.setAddress1(null);
        member.setAddress2(null);
        member.setAuthProvider(profile.provider());
        member.setProviderUserId(profile.providerUserId());
        member.setProfileImageUrl(trimTo(profile.profileImageUrl(), 512));
        member.setRole(MemberRole.CUSTOMER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        Member saved = memberRepository.save(member);
        log.info(
                "Social signup success provider={} memberId={}",
                profile.provider(),
                saved.getMemberId());
        return saved;
    }

    private void refreshProfileHints(Member member, SocialProfile profile) {
        boolean changed = false;
        if ((member.getName() == null || member.getName().isBlank()) && profile.name() != null) {
            member.setName(trimTo(profile.name(), 100));
            changed = true;
        }
        if (member.getProfileImageUrl() == null && profile.profileImageUrl() != null) {
            member.setProfileImageUrl(trimTo(profile.profileImageUrl(), 512));
            changed = true;
        }
        if (member.getEmail() == null && profile.email() != null) {
            String email = AuthService.normalizeEmail(profile.email());
            if (!memberRepository.existsByEmail(email)) {
                member.setEmail(email);
                changed = true;
            }
        }
        if (changed) {
            member.setUpdatedAt(clock.instant());
            memberRepository.save(member);
        }
    }

    /**
     * Never auto-link a social account to an existing LOCAL (or other) email identity.
     */
    private String resolveEmail(SocialProfile profile) {
        if (profile.email() == null || profile.email().isBlank()) {
            return null;
        }
        String email = AuthService.normalizeEmail(profile.email());
        if (memberRepository.existsByEmail(email)) {
            log.info(
                    "Social email already registered; leaving email unset provider={} email={}",
                    profile.provider(),
                    maskEmail(email));
            return null;
        }
        return email;
    }

    private String generateLoginId(AuthProvider provider) {
        String prefix = switch (provider) {
            case KAKAO -> "kakao_";
            case NAVER -> "naver_";
            default -> "social_";
        };
        for (int i = 0; i < 8; i++) {
            String candidate = prefix + randomSuffix(20 - prefix.length());
            if (!memberRepository.existsByLoginId(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "소셜 로그인 아이디를 생성하지 못했습니다.");
    }

    private static String randomSuffix(int length) {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String defaultName(AuthProvider provider) {
        return provider == AuthProvider.NAVER ? "네이버회원" : "카카오회원";
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return "***@" + email.substring(at + 1).toLowerCase(Locale.ROOT);
    }
}
