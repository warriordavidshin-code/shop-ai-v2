package com.petitcamel.shop.payment.gateway;

public record PaymentApproveResult(
        boolean approved,
        String providerTransactionId,
        String message
) {
    public static PaymentApproveResult success(String providerTransactionId) {
        return new PaymentApproveResult(true, providerTransactionId, "APPROVED");
    }

    public static PaymentApproveResult failure(String message) {
        return new PaymentApproveResult(false, null, message);
    }
}
