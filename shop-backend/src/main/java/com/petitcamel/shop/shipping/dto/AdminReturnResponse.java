package com.petitcamel.shop.shipping.dto;

import java.math.BigDecimal;

public record AdminReturnResponse(
        ReturnRequestResponse returnRequest,
        String orderNo,
        String orderStatus,
        Long memberId,
        String memberLoginId,
        String memberName,
        String itemSummary,
        BigDecimal paymentAmount,
        String shipmentStatus,
        String shipmentStatusName
) {
}
