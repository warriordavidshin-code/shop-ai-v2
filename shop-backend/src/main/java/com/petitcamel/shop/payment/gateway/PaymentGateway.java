package com.petitcamel.shop.payment.gateway;

public interface PaymentGateway {

    PaymentApproveResult approve(PaymentApproveCommand command);
}
