package com.petitcamel.shop.order.dto;

import com.petitcamel.shop.order.domain.CancelRequestStatus;

import java.time.Instant;

public record CancelRequestInfo(
        Long cancelRequestId,
        CancelRequestStatus status,
        String reason,
        String rejectReason,
        Instant requestedAt,
        Instant processedAt
) {
}
