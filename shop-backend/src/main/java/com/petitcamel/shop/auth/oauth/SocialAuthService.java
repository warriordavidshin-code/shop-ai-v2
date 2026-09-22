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

    /**
     * Kakao/Naver 공통: provider 식별자로 회원을 찾고,
     * 없으면 소셜 정보로 자동 회원가입한 뒤 즉시 로그인(세션 발급)한다.
     */
    @Transactional
    public SocialAuthOutcome loginOrSignup(SocialProfile profile) {
        if (profile.provider() == null || profile.provider() == AuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 소셜 로그인입니다.");
        }
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "소셜 사용자 식별자를 확인할 수 없습니다.");
        }

        Optional<Member> existing = memberRepository.findByAuthProviderAndProviderUserId(
                profile.provider(), profile.providerUserId());
        if (existing.isPresent()) {
            Member member = existing.get();
            refreshProfileHints(member, profile);
            AuthService.AuthResult session = authService.completeAuthenticatedSession(member);
            log.info(
                    "Social login success provider={} memberId={}",
                    profile.provider(),
                    member.getMemberId());
            return new SocialAuthOutcome(session, false);
        }

        Member created = createSocialMember(profile);
        AuthService.AuthResult session = authService.completeAuthenticatedSession(created);
        log.info(
                "Social auto-signup then login provider={} memberId={}",
                profile.provider(),
                created.getMemberId());
        return new SocialAuthOutcome(session, true);
    }

    public record SocialAuthOutcome(AuthService.AuthResult authResult, boolean newlyRegistered) {
    }

    private Member createSocialMember(SocialProfile profile) {
        Instant now = clock.instant();
        Member member = new Member();
        member.setLoginId(generateLoginId(profile.provider()));
        member.setEmail(resolveEmail(profile));
        member.setPasswordHash(null);
        member.setName(trimTo(
                profile.name() == null || profile.name().isBlank()
                        ? defaultName(profile.provider())
                        : profile.name(),
                100));
        member.setBirthDate(profile.birthDate());
        member.setGender(profile.gender() == null ? Gender.PREFER_NOT_TO_SAY : profile.gender());
        member.setPhone(trimTo(profile.phone(), 32));
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
                "Social signup success provider={} memberId={} ageRange={}",
                profile.provider(),
                saved.getMemberId(),
                profile.ageRange());
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
        if ((member.getPhone() == null || member.getPhone().isBlank()) && profile.phone() != null) {
            member.setPhone(trimTo(profile.phone(), 32));
            changed = true;
        }
        if (member.getGender() == null && profile.gender() != null) {
            member.setGender(profile.gender());
            changed = true;
        }
        if (member.getBirthDate() == null && profile.birthDate() != null) {
            member.setBirthDate(profile.birthDate());
            changed = true;
        }
        if (changed) {
            member.setUpdatedAt(clock.instant());
            memberRepository.save(member);
        }
    }

    /**
     * Kakao requires email. Never auto-link to an existing LOCAL/other account email.
     */
    private String resolveEmail(SocialProfile profile) {
        if (profile.email() == null || profile.email().isBlank()) {
            if (profile.provider() == AuthProvider.KAKAO) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "카카오 이메일 동의가 필요합니다. 동의 후 다시 시도해 주세요.");
            }
            return null;
        }
        String email = AuthService.normalizeEmail(profile.email());
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "이미 가입된 이메일입니다. 기존 계정으로 로그인해 주세요.");
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
}
