package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ShippingOperationStatus;
import com.petitcamel.shop.shipping.domain.ShippingOperationType;

import java.time.Instant;

/** @param needsAttention vendor outcome unknown, or succeeded but not saved to the shipment yet */
public record ShippingApiOperationResponse(
        Long operationId,
        String providerCode,
        ShippingOperationType operationType,
        Long orderId,
        Long shipmentId,
        Long returnRequestId,
        String idempotencyKey,
        String requestId,
        String externalReference,
        ShippingOperationStatus status,
        Integer httpStatus,
        String errorCode,
        String errorMessage,
        int attemptCount,
        Instant requestedAt,
        Instant completedAt,
        Instant appliedAt,
        boolean needsAttention
) {
}
