package com.petitcamel.shop.shipping.domain;

/** Where a surcharge area row came from; OFFICIAL rows can be replaced wholesale by a courier data import. */
public enum ExtraAreaSource {
    MANUAL,
    OFFICIAL
}
