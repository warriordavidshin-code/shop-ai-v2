package com.petitcamel.shop.member.dto;

import com.petitcamel.shop.member.domain.AuthProvider;

import java.time.Instant;

public record ReauthStatusResponse(
        boolean verified,
        Instant expiresAt,
        AuthProvider method
) {
}
