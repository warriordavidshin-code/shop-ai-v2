package com.petitcamel.shop.inventory.dto;

import com.petitcamel.shop.inventory.domain.MovementType;

import java.time.Instant;

public record InventoryMovementResponse(
        Long movementId,
        Long skuId,
        MovementType movementType,
        int quantity,
        String referenceType,
        String referenceId,
        String reason,
        Long actorMemberId,
        Instant createdAt
) {
}
