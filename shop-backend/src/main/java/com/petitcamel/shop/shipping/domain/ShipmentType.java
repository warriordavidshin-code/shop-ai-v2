package com.petitcamel.shop.shipping.domain;

/** Direction of a shipment: to the customer, or back from the customer (return pickup). */
public enum ShipmentType {
    DELIVERY,
    RETURN
}
