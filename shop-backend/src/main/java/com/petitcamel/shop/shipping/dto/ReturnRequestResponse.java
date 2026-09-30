package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ReturnReason;
import com.petitcamel.shop.shipping.domain.ReturnStatus;

import java.time.Instant;

/**
 * Return request plus the parcel fields of its RETURN shipment (pickup address, courier, invoice, shipment status),
 * so screens get the whole return in one object.
 */
public record ReturnRequestResponse(
        Long returnRequestId,
        Long orderId,
        ReturnReason reason,
        String reasonLabel,
        String reasonText,
        String customerMemo,
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
        String shipmentStatus,
        String shipmentStatusName,
        boolean freeReturn,
        long returnShippingFee,
        Long refundAmount,
        boolean restocked,
        String rejectReason,
        Instant requestedAt,
        Instant approvedAt,
        Instant pickupRequestedAt,
        Instant pickedUpAt,
        Instant receivedAt,
        Instant completedAt,
        Instant rejectedAt,
        Instant cancelledAt
) {
}
