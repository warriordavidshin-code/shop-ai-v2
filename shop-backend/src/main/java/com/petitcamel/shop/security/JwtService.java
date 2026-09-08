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

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId, String email, MemberRole role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(properties.getAccessTokenSeconds());
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("memberId", memberId)
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long memberId = claims.get("memberId", Long.class);
            if (memberId == null && claims.getSubject() != null) {
                memberId = Long.valueOf(claims.getSubject());
            }
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            return new AccessTokenClaims(memberId, email, MemberRole.valueOf(role));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("유효하지 않은 액세스 토큰입니다.", ex);
        }
    }

    public long getAccessTokenSeconds() {
        return properties.getAccessTokenSeconds();
    }

    public long getRefreshTokenSeconds() {
        return properties.getRefreshTokenSeconds();
    }

    public record AccessTokenClaims(Long memberId, String email, MemberRole role) {
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
