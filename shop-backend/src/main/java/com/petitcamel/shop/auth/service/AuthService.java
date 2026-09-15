package com.petitcamel.shop.auth.service;

import com.petitcamel.shop.auth.dto.LoginRequest;
import com.petitcamel.shop.auth.dto.PasswordResetRequest;
import com.petitcamel.shop.auth.dto.PasswordResetResponse;
import com.petitcamel.shop.auth.dto.SignupRequest;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.mail.MailService;
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

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final String LOGIN_FAILURE_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";
    public static final String PASSWORD_RESET_NOT_FOUND_MESSAGE = "입력하신 아이디와 이름과 일치하는 회원을 찾을 수 없습니다.";

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final MailService mailService;
    private final Clock clock;

    public AuthService(
            MemberRepository memberRepository,
            MemberService memberService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            MailService mailService,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.mailService = mailService;
        this.clock = clock;
    }

    @Transactional
    public AuthResult signup(SignupRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        if (memberRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "이메일은 필수입니다.");
        }
        String email = normalizeEmail(request.email());
        if (memberRepository.existsByEmail(email)) {
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

    @Transactional
    public PasswordResetResponse resetPassword(PasswordResetRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        String name = request.name().trim();
        Member member = memberRepository.findByLoginIdAndName(loginId, name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, PASSWORD_RESET_NOT_FOUND_MESSAGE));

        assertMemberActive(member);

        if (member.getAuthProvider() != AuthProvider.LOCAL || member.getPasswordHash() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "소셜 로그인 계정은 비밀번호 찾기를 사용할 수 없습니다. 카카오/네이버로 로그인해 주세요.");
        }
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "등록된 이메일이 없어 임시 비밀번호를 발송할 수 없습니다.");
        }

        String temporaryPassword = generateTemporaryPassword();
        member.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        member.setUpdatedAt(clock.instant());
        memberRepository.save(member);
        refreshTokenService.revokeAllForMember(member.getMemberId());

        String masked = maskEmail(member.getEmail());
        mailService.sendPlainText(
                member.getEmail(),
                "[BoutiqueCamel] 임시 비밀번호 안내",
                """
                안녕하세요, BoutiqueCamel 입니다.

                요청하신 계정(%s)의 임시 비밀번호는 아래와 같습니다.

                임시 비밀번호: %s

                로그인 후 반드시 비밀번호를 변경해 주세요.
                본인이 요청하지 않았다면 고객센터로 문의해 주세요.

                https://btc-camel.com
                """.formatted(loginId, temporaryPassword));

        log.info("Temporary password issued memberId={} email={}", member.getMemberId(), masked);
        return new PasswordResetResponse(
                "등록된 이메일(" + masked + ")로 임시 비밀번호를 발송했습니다. 로그인 후 비밀번호를 변경해 주세요.",
                masked);
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

    static String generateTemporaryPassword() {
        final String letters = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        final String digits = "23456789";
        final String specials = "!@#$%";
        StringBuilder sb = new StringBuilder(10);
        sb.append(letters.charAt(SECURE_RANDOM.nextInt(letters.length())));
        sb.append(digits.charAt(SECURE_RANDOM.nextInt(digits.length())));
        sb.append(specials.charAt(SECURE_RANDOM.nextInt(specials.length())));
        String all = letters + digits + specials;
        for (int i = 0; i < 7; i++) {
            sb.append(all.charAt(SECURE_RANDOM.nextInt(all.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
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

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***@" + (at > 0 ? email.substring(at + 1) : "");
        }
        return email.charAt(0) + "***@" + email.substring(at + 1);
    }

    public record AuthResult(MemberResponse member, String accessToken, String refreshToken) {
    }
}
