package com.petitcamel.shop.shipping.domain;

/** INTERNAL events are written by the shop (admin actions); PROVIDER events come from a tracking API. */
public enum TrackingEventSource {
    INTERNAL,
    PROVIDER
}
