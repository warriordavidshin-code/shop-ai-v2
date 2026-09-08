package com.petitcamel.shop.payment.gateway;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock payment gateway for MVP. Never calls a real PG.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentApproveResult approve(PaymentApproveCommand command) {
        if (command == null || command.orderNo() == null || command.orderNo().isBlank()) {
            return PaymentApproveResult.failure("주문번호가 없습니다.");
        }
        if (command.amount() == null || command.amount().signum() < 0) {
            return PaymentApproveResult.failure("결제 금액이 올바르지 않습니다.");
        }
        String txId = "MOCK-" + command.orderNo() + "-" + UUID.randomUUID().toString().substring(0, 8);
        return PaymentApproveResult.success(txId);
    }
}
