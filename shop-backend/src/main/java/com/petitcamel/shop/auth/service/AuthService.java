package com.petitcamel.shop.auth.service;

import com.petitcamel.shop.auth.dto.LoginRequest;
import com.petitcamel.shop.auth.dto.SignupRequest;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
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

    public static final String LOGIN_FAILURE_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

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
        String email = normalizeEmail(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }
        memberService.validateAgePolicy(request.birthDate());

        Instant now = clock.instant();
        Member member = new Member();
        member.setEmail(email);
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        member.setName(request.name().trim());
        member.setBirthDate(request.birthDate());
        member.setGender(request.gender());
        member.setPhone(request.phone().trim());
        member.setPostcode(request.postcode().trim());
        member.setAddress1(request.address1().trim());
        member.setAddress2(request.address2() == null ? null : request.address2().trim());
        member.setRole(MemberRole.CUSTOMER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        Member saved = memberRepository.save(member);
        log.info("Signup success memberId={} emailDomain={}",
                saved.getMemberId(), emailDomain(email));
        return issueTokens(saved);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login failed: unknown email domain={}", emailDomain(email));
                    return new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
                });

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            log.warn("Login failed: bad password memberId={}", member.getMemberId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, LOGIN_FAILURE_MESSAGE);
        }

        if (member.getStatus() == MemberStatus.BLOCKED || member.getStatus() == MemberStatus.WITHDRAWN) {
            log.warn("Login blocked memberId={} status={}", member.getMemberId(), member.getStatus());
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "이용이 제한된 계정입니다. 고객센터에 문의해 주세요.");
        }

        member.setLastLoginAt(clock.instant());
        member.setUpdatedAt(clock.instant());
        memberRepository.save(member);
        log.info("Login success memberId={} role={}", member.getMemberId(), member.getRole());
        return issueTokens(member);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedTokens rotated = refreshTokenService.rotate(rawRefreshToken);
        Member member = memberService.requireMember(rotated.memberId());
        if (member.getStatus() == MemberStatus.BLOCKED || member.getStatus() == MemberStatus.WITHDRAWN) {
            refreshTokenService.revoke(rotated.rawRefreshToken());
            log.warn("Refresh rejected memberId={} status={}", member.getMemberId(), member.getStatus());
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "이용이 제한된 계정입니다. 고객센터에 문의해 주세요.");
        }
        String accessToken = jwtService.createAccessToken(
                member.getMemberId(), member.getEmail(), member.getRole());
        log.info("Refresh token rotated memberId={}", member.getMemberId());
        return new AuthResult(memberService.toResponse(member), accessToken, rotated.rawRefreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
        log.info("Logout completed (refresh revoked)");
    }

    private AuthResult issueTokens(Member member) {
        String accessToken = jwtService.createAccessToken(
                member.getMemberId(), member.getEmail(), member.getRole());
        String refreshToken = refreshTokenService.issue(member.getMemberId());
        return new AuthResult(memberService.toResponse(member), accessToken, refreshToken);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String emailDomain(String email) {
        int at = email.indexOf('@');
        return at >= 0 ? email.substring(at + 1) : "unknown";
    }

    public record AuthResult(MemberResponse member, String accessToken, String refreshToken) {
    }
}
