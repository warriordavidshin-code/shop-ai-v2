package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.order.dto.OrderResponse;
import com.petitcamel.shop.shipping.dto.ReturnRequestResponse;
import com.petitcamel.shop.shipping.dto.ShipmentView;

public record AdminOrderDetailResponse(
        OrderResponse order,
        String memberLoginId,
        String memberName,
        ShipmentView delivery,
        ShipmentView returnShipment,
        ReturnRequestResponse returnRequest
) {
}
