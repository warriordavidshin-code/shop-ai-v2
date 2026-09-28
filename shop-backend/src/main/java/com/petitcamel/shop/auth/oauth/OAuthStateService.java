package com.petitcamel.shop.auth.oauth;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived OAuth {@code state} store (CSRF protection).
 * Single-node in-memory is enough for local/dev; use a shared store for multi-instance prod.
 */
@Service
public class OAuthStateService {

    private static final long TTL_SECONDS = 600;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public OAuthStateService(Clock clock) {
        this.clock = clock;
    }

    public String issue(AuthProvider provider, String redirectPath) {
        return put(new StateEntry(provider, sanitizeRedirect(redirectPath), Purpose.LOGIN, null,
                clock.instant().plusSeconds(TTL_SECONDS)));
    }

    /** State for re-verifying an already logged-in member; the callback must not log anyone in. */
    public String issueReauth(AuthProvider provider, Long memberId, String redirectPath) {
        return put(new StateEntry(provider, sanitizeRedirect(redirectPath), Purpose.REAUTH, memberId,
                clock.instant().plusSeconds(TTL_SECONDS)));
    }

    private String put(StateEntry entry) {
        purgeExpired();
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, entry);
        return state;
    }

    public OAuthState consume(String state, AuthProvider expectedProvider) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 OAuth state 입니다.");
        }
        StateEntry entry = states.remove(state);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "만료되었거나 유효하지 않은 OAuth state 입니다.");
        }
        if (entry.provider() != expectedProvider) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth provider 가 일치하지 않습니다.");
        }
        return new OAuthState(entry.redirectPath(), entry.purpose(), entry.memberId());
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        states.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    static String sanitizeRedirect(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            return "/";
        }
        return path;
    }

    public enum Purpose {
        LOGIN,
        REAUTH
    }

    public record OAuthState(String redirectPath, Purpose purpose, Long memberId) {
    }

    private record StateEntry(
            AuthProvider provider,
            String redirectPath,
            Purpose purpose,
            Long memberId,
            Instant expiresAt) {
    }
}
