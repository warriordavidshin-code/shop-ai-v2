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
        purgeExpired();
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, new StateEntry(provider, sanitizeRedirect(redirectPath), clock.instant().plusSeconds(TTL_SECONDS)));
        return state;
    }

    public String consume(String state, AuthProvider expectedProvider) {
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
        return entry.redirectPath();
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

    private record StateEntry(AuthProvider provider, String redirectPath, Instant expiresAt) {
    }
}
