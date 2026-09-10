package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.member.domain.MemberRole;
import com.petitcamel.shop.member.domain.MemberStatus;

import java.time.Instant;

public record AdminMemberResponse(
        Long memberId,
        String loginId,
        String email,
        String name,
        MemberRole role,
        MemberStatus status,
        Instant createdAt,
        Instant lastLoginAt
) {
}
