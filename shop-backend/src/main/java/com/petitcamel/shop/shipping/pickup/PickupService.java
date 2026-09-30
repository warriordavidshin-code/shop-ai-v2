package com.petitcamel.shop.shipping.pickup;

/**
 * Courier pickup (집하/반품 수거) request. The default implementation is manual: the admin books the
 * pickup with the courier and records it here. A Goodsflow/courier API implementation can book it
 * automatically and return the issued invoice number.
 */
public interface PickupService {

    String name();

    PickupResponse requestPickup(PickupRequest request);
}
