package com.petitcamel.shop.auth.service;

import com.petitcamel.shop.auth.dto.LoginRequest;
import com.petitcamel.shop.auth.dto.SignupRequest;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.member.domain.MemberStatus;
import com.petitcamel.shop.member.dto.MemberResponse;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.member.service.MemberService;
import com.petitcamel.shop.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String LOGIN_FAILURE_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public AuthService(
            MemberRepository memberRepository,
            MemberService memberService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthResult signup(SignupRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        if (memberRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        String email = request.email() == null ? null : normalizeEmail(request.email());
        if (email != null && memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }
        memberService.validateAgePolicy(request.birthDate());

        Instant now = clock.instant();
        Member member = new Member();
        member.setLoginId(loginId);
        member.setEmail(email);
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        member.setName(request.name().trim());
        member.setBirthDate(request.birthDate());
        member.setGender(request.gender());
        member.setPhone(request.phone().trim());
        member.setPostcode(request.postcode().trim());
        member.setAddress1(request.address1().trim());
        member.setAddress2(request.address2() == null ? null : request.address2().trim());
        member.setAuthProvider(AuthProvider.LOCAL);
        member.setProviderUserId(null);
        member.setRole(MemberRole.CUSTOMER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setLastLoginAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        Member saved = memberRepository.save(member);
        log.info("Signup success memberId={} loginId={}", saved.getMemberId(), loginId);
        return issueTokens(saved);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String rawId = request.loginId();
        Member member = resolveMember(rawId)
                .orElseThrow(() -> {
                    log.warn("Login failed: unknown loginId={}", maskLoginId(rawId));
                    return new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
                });

        if (member.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            log.warn("Login failed: bad password memberId={}", member.getMemberId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
        }

        return completeAuthenticatedSession(member);
    }

    /**
     * Shared session issuance for local login/signup and social OAuth callbacks.
     */
    @Transactional
    public AuthResult completeAuthenticatedSession(Member member) {
        assertMemberActive(member);
        Instant now = clock.instant();
        member.setLastLoginAt(now);
        member.setUpdatedAt(now);
        memberRepository.save(member);
        log.info(
                "Auth session issued memberId={} provider={} role={}",
                member.getMemberId(),
                member.getAuthProvider(),
                member.getRole());
        return issueTokens(member);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedTokens rotated = refreshTokenService.rotate(rawRefreshToken);
        Member member = memberService.requireMember(rotated.memberId());
        assertMemberActive(member);
        String accessToken = jwtService.createAccessToken(
                member.getMemberId(), member.getLoginId(), member.getRole());
        log.info("Refresh token rotated memberId={}", member.getMemberId());
        return new AuthResult(memberService.toResponse(member), accessToken, rotated.rawRefreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
        log.info("Logout completed (refresh revoked)");
    }

    private void assertMemberActive(Member member) {
        if (member.getStatus() == MemberStatus.BLOCKED || member.getStatus() == MemberStatus.WITHDRAWN) {
            log.warn("Auth blocked memberId={} status={}", member.getMemberId(), member.getStatus());
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "이용이 제한된 계정입니다. 고객센터에 문의해 주세요.");
        }
    }

    private AuthResult issueTokens(Member member) {
        String accessToken = jwtService.createAccessToken(
                member.getMemberId(), member.getLoginId(), member.getRole());
        String refreshToken = refreshTokenService.issue(member.getMemberId());
        return new AuthResult(memberService.toResponse(member), accessToken, refreshToken);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeLoginId(String loginId) {
        return loginId.trim().toLowerCase(Locale.ROOT);
    }

    private java.util.Optional<Member> resolveMember(String rawId) {
        if (rawId.contains("@")) {
            return memberRepository.findByEmail(normalizeEmail(rawId));
        }
        return memberRepository.findByLoginId(normalizeLoginId(rawId));
    }

    private static String maskLoginId(String loginId) {
        if (loginId.contains("@")) {
            int at = loginId.indexOf('@');
            return "***@" + loginId.substring(at + 1).toLowerCase(Locale.ROOT);
        }
        if (loginId.length() <= 2) {
            return "***";
        }
        return loginId.charAt(0) + "***";
    }

    public record AuthResult(MemberResponse member, String accessToken, String refreshToken) {
    }
}
