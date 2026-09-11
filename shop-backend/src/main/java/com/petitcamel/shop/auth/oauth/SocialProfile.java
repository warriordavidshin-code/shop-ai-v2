package com.petitcamel.shop.auth.oauth;

import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Gender;

import java.time.LocalDate;

public record SocialProfile(
        AuthProvider provider,
        String providerUserId,
        String email,
        String name,
        String profileImageUrl,
        LocalDate birthDate,
        Gender gender
) {
}
