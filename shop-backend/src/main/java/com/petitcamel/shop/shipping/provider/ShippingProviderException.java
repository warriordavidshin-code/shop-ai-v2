package com.petitcamel.shop.shipping.provider;

/** Provider failure. The message must be safe to log and show (no API keys, no personal data). */
public class ShippingProviderException extends RuntimeException {

    public ShippingProviderException(String message) {
        super(message);
    }

    public ShippingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
