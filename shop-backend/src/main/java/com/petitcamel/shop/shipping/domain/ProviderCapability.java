package com.petitcamel.shop.shipping.domain;

/** Features an external shipping API vendor may offer. */
public enum ProviderCapability {
    TRACKING("배송조회"),
    WAYBILL("송장발급"),
    PICKUP("집하요청"),
    RETURN_PICKUP("반품수거");

    private final String label;

    ProviderCapability(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
