package com.petitcamel.shop.shipping.waybill;

public record WaybillRequest(
        Long orderId,
        String orderNo,
        String deliveryCompany
) {
}
