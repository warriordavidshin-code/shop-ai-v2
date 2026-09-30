package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ReturnReason;
import com.petitcamel.shop.shipping.domain.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ReturnRequestResponse(
        Long returnRequestId,
        Long orderId,
        ReturnReason reason,
        String reasonLabel,
        String memo,
        ReturnStatus status,
        String statusName,
        String pickupName,
        String pickupPhone,
        String pickupPostcode,
        String pickupAddress1,
        String pickupAddress2,
        String pickupDeliveryCompany,
        String pickupDeliveryCompanyName,
        String pickupTrackingNumber,
        String pickupTrackingUrl,
        boolean freeReturn,
        BigDecimal returnShippingFee,
        BigDecimal refundAmount,
        boolean restocked,
        String rejectReason,
        Instant requestedAt,
        Instant approvedAt,
        Instant pickupRequestedAt,
        Instant pickedUpAt,
        Instant receivedAt,
        Instant completedAt,
        Instant rejectedAt
) {
}
