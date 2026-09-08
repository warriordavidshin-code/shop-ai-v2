package com.petitcamel.shop.auth.service;

import com.petitcamel.shop.auth.domain.RefreshToken;
import com.petitcamel.shop.auth.repository.RefreshTokenRepository;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.security.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public String issue(Long memberId) {
        String rawToken = generateRawToken();
        Instant now = clock.instant();
        RefreshToken entity = new RefreshToken();
        entity.setMemberId(memberId);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(now.plusSeconds(jwtProperties.getRefreshTokenSeconds()));
        entity.setCreatedAt(now);
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Transactional
    public RotatedTokens rotate(String rawRefreshToken) {
        RefreshToken existing = findValid(rawRefreshToken);
        existing.setRevokedAt(clock.instant());
        refreshTokenRepository.save(existing);
        String newRaw = issue(existing.getMemberId());
        return new RotatedTokens(existing.getMemberId(), newRaw);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional(readOnly = true)
    public RefreshToken requireValid(String rawRefreshToken) {
        return findValid(rawRefreshToken);
    }

    private RefreshToken findValid(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "리프레시 토큰이 필요합니다.");
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."));
        Instant now = clock.instant();
        if (token.getRevokedAt() != null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "폐기된 리프레시 토큰입니다.");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "만료된 리프레시 토큰입니다.");
        }
        return token;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotatedTokens(Long memberId, String rawRefreshToken) {
    }
}
