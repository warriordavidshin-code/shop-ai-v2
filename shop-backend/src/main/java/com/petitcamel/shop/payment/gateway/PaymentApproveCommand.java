package com.petitcamel.shop.payment.gateway;

import java.math.BigDecimal;

public record PaymentApproveCommand(
        String orderNo,
        Long paymentId,
        BigDecimal amount,
        String paymentMethod
) {
}
