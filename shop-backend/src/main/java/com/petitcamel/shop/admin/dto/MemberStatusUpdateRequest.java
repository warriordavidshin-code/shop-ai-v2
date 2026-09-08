package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.member.domain.MemberStatus;
import jakarta.validation.constraints.NotNull;

public record MemberStatusUpdateRequest(
        @NotNull MemberStatus status
) {
}
