package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.domain.ShipmentType;

/**
 * Input for state-changing vendor calls (waybill issue, pickup, return pickup).
 *
 * @param requestId           our reference for this call; vendors that accept a client reference get it so the
 *                            call can be looked up again after a crash
 * @param externalCompanyCode courier code in the vendor's code system, or null to let the vendor choose
 * @param contact             customer side: delivery destination, or pickup origin for returns
 */
public record ShipmentCommand(
        String requestId,
        Long orderId,
        String orderNo,
        Long shipmentId,
        ShipmentType shipmentType,
        String deliveryCompanyCode,
        String externalCompanyCode,
        Contact contact,
        String memo
) {

    public record Contact(String name, String phone, String postalCode, String address1, String address2) {
    }
}
