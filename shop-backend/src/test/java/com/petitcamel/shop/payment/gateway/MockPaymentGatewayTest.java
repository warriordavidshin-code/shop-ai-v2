package com.petitcamel.shop.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void approveSucceedsWithTransactionId() {
        PaymentApproveResult result = gateway.approve(new PaymentApproveCommand(
                "PC202609041200001234",
                1L,
                new BigDecimal("52000"),
                "MOCK"));

        assertThat(result.approved()).isTrue();
        assertThat(result.providerTransactionId()).startsWith("MOCK-PC202609041200001234-");
        assertThat(result.message()).isEqualTo("APPROVED");
    }

    @Test
    void approveFailsWhenOrderNoMissing() {
        PaymentApproveResult result = gateway.approve(new PaymentApproveCommand(
                "  ",
                1L,
                BigDecimal.TEN,
                "MOCK"));

        assertThat(result.approved()).isFalse();
        assertThat(result.providerTransactionId()).isNull();
    }

    @Test
    void approveFailsWhenAmountNegative() {
        PaymentApproveResult result = gateway.approve(new PaymentApproveCommand(
                "PC123",
                1L,
                new BigDecimal("-1"),
                "MOCK"));

        assertThat(result.approved()).isFalse();
    }
}
