package com.petitcamel.shop.shipping.support;

/** Keeps invoice numbers and secrets out of logs. */
public final class ShippingLogMasker {

    private static final int VISIBLE_PREFIX = 4;

    private ShippingLogMasker() {
    }

    /** "123456789012" -> "1234********". */
    public static String maskTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return "-";
        }
        String value = trackingNumber.trim();
        if (value.length() <= VISIBLE_PREFIX) {
            return "*".repeat(value.length());
        }
        return value.substring(0, VISIBLE_PREFIX) + "*".repeat(value.length() - VISIBLE_PREFIX);
    }

    /** Removes {@code secret} (e.g. an API key embedded in a request URL) from an error message. */
    public static String scrub(String message, String secret) {
        if (message == null) {
            return null;
        }
        String result = message;
        if (secret != null && !secret.isBlank()) {
            result = result.replace(secret, "****");
        }
        return result.length() > 200 ? result.substring(0, 200) : result;
    }
}
