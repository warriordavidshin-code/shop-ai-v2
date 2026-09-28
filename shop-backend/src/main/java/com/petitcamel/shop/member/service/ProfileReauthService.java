package com.petitcamel.shop.member.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.dto.ReauthStatusResponse;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.security.AuthCookieService;
import com.petitcamel.shop.security.JwtService;
import com.petitcamel.shop.security.JwtService.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProfileReauthService {

    public static final Duration REAUTH_TTL = Duration.ofMinutes(10);
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthCookieService authCookieService;
    private final Clock clock;
    private final Map<Long, FailureWindow> failures = new ConcurrentHashMap<>();

    public ProfileReauthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthCookieService authCookieService,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReauthToken verifyPassword(Long memberId, String password) {
        Member member = requireMember(memberId);
        if (member.getAuthProvider() != AuthProvider.LOCAL || !StringUtils.hasText(member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "소셜 로그인 회원은 소셜 계정으로 본인 인증을 진행해 주세요.");
        }
        Instant now = clock.instant();
        ensureNotLocked(memberId, now);
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            recordFailure(memberId, now);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }
        failures.remove(memberId);
        return issue(memberId, AuthProvider.LOCAL, now);
    }

    @Transactional(readOnly = true)
    public ReauthToken verifySocial(Long memberId, AuthProvider provider, String providerUserId) {
        Member member = requireMember(memberId);
        if (member.getAuthProvider() != provider
                || !StringUtils.hasText(providerUserId)
                || !providerUserId.equals(member.getProviderUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "로그인한 계정과 인증한 소셜 계정이 다릅니다.");
        }
        return issue(memberId, provider, clock.instant());
    }

    public ReauthStatusResponse status(HttpServletRequest request, Long memberId) {
        AuthProvider method = requireMember(memberId).getAuthProvider();
        return readValid(request, memberId)
                .map(claims -> new ReauthStatusResponse(true, claims.expiresAt(), method))
                .orElseGet(() -> new ReauthStatusResponse(false, null, method));
    }

    public void requireVerified(HttpServletRequest request, Long memberId) {
        if (readValid(request, memberId).isEmpty()) {
            throw new BusinessException(ErrorCode.REAUTH_REQUIRED,
                    "개인정보 수정을 위해 본인 인증이 필요합니다.");
        }
    }

    private Optional<JwtService.ReauthClaims> readValid(HttpServletRequest request, Long memberId) {
        return authCookieService.readCookie(request, AuthCookieService.REAUTH_COOKIE)
                .flatMap(token -> {
                    try {
                        return Optional.of(jwtService.parseReauthToken(token));
                    } catch (InvalidTokenException ex) {
                        return Optional.empty();
                    }
                })
                .filter(claims -> claims.memberId().equals(memberId));
    }

    private ReauthToken issue(Long memberId, AuthProvider method, Instant now) {
        String token = jwtService.createReauthToken(memberId, method.name(), REAUTH_TTL.toSeconds());
        return new ReauthToken(token, now.plus(REAUTH_TTL), REAUTH_TTL);
    }

    private void ensureNotLocked(Long memberId, Instant now) {
        FailureWindow window = failures.get(memberId);
        if (window != null && window.startedAt().plus(FAILURE_WINDOW).isBefore(now)) {
            failures.remove(memberId, window);
            return;
        }
        if (window != null && window.count() >= MAX_FAILED_ATTEMPTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "비밀번호 확인에 여러 번 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void recordFailure(Long memberId, Instant now) {
        failures.compute(memberId, (id, window) -> {
            if (window == null || window.startedAt().plus(FAILURE_WINDOW).isBefore(now)) {
                return new FailureWindow(now, 1);
            }
            return new FailureWindow(window.startedAt(), window.count() + 1);
        });
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    public record ReauthToken(String token, Instant expiresAt, Duration maxAge) {
    }

    private record FailureWindow(Instant startedAt, int count) {
    }
}
