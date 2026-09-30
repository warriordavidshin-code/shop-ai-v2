package com.petitcamel.shop.shipping.provider;

/**
 * Courier tracking integration. The shop only talks to this interface; switching courier/aggregator
 * (SweetTracker, Goodsflow, a courier's own API) means adding or selecting another implementation.
 */
public interface ShippingProvider {

    /** Value used in {@code SHIPPING_PROVIDER} and {@code delivery_company_code.provider}. */
    String name();

    /** False when required settings (API key, endpoint) are missing; the registry then falls back to MANUAL. */
    boolean isConfigured();

    /**
     * @param companyCode    courier code in this provider's own code system
     * @param trackingNumber invoice number
     * @throws ShippingProviderException when the provider cannot be reached or rejects the request
     */
    TrackingResponse tracking(String companyCode, String trackingNumber);
}
