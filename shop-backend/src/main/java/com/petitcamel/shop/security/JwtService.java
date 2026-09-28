package com.petitcamel.shop.security;

import com.petitcamel.shop.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String REAUTH_PURPOSE = "profile_reauth";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId, String loginId, MemberRole role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(properties.getAccessTokenSeconds());
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("memberId", memberId)
                .claim("loginId", loginId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (claims.get(PURPOSE_CLAIM) != null) {
                throw new InvalidTokenException("액세스 토큰이 아닙니다.", null);
            }
            Long memberId = claims.get("memberId", Long.class);
            if (memberId == null && claims.getSubject() != null) {
                memberId = Long.valueOf(claims.getSubject());
            }
            String loginId = claims.get("loginId", String.class);
            if (loginId == null) {
                loginId = claims.get("email", String.class);
            }
            String role = claims.get("role", String.class);
            if (memberId == null || role == null) {
                throw new InvalidTokenException("유효하지 않은 액세스 토큰입니다.", null);
            }
            return new AccessTokenClaims(memberId, loginId, MemberRole.valueOf(role));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("유효하지 않은 액세스 토큰입니다.", ex);
        }
    }

    /**
     * Short-lived proof that the member re-entered credentials (password or social re-login)
     * before editing personal information. Rejected by {@link #parseAccessToken}.
     */
    public String createReauthToken(Long memberId, String method, long ttlSeconds) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(PURPOSE_CLAIM, REAUTH_PURPOSE)
                .claim("memberId", memberId)
                .claim("method", method)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public ReauthClaims parseReauthToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!REAUTH_PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
                throw new InvalidTokenException("본인 인증 토큰이 아닙니다.", null);
            }
            Long memberId = claims.get("memberId", Long.class);
            if (memberId == null) {
                throw new InvalidTokenException("유효하지 않은 본인 인증 토큰입니다.", null);
            }
            return new ReauthClaims(memberId, claims.get("method", String.class), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("유효하지 않은 본인 인증 토큰입니다.", ex);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenSeconds() {
        return properties.getAccessTokenSeconds();
    }

    public long getRefreshTokenSeconds() {
        return properties.getRefreshTokenSeconds();
    }

    public record AccessTokenClaims(Long memberId, String loginId, MemberRole role) {
    }

    public record ReauthClaims(Long memberId, String method, Instant expiresAt) {
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
