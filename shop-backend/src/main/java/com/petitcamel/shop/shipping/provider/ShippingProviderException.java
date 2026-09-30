package com.petitcamel.shop.shipping.provider;

/**
 * Vendor failure. The message must be safe to log and show (no API keys, no personal data).
 * {@code outcomeUnknown} is true when the vendor may have processed the request anyway (e.g. read timeout), so a
 * state-changing call must not simply be retried.
 */
public class ShippingProviderException extends RuntimeException {

    private final Integer httpStatus;
    private final String errorCode;
    private final boolean outcomeUnknown;

    public ShippingProviderException(String message) {
        this(message, null, null, false);
    }

    public ShippingProviderException(String message, Integer httpStatus, String errorCode, boolean outcomeUnknown) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.outcomeUnknown = outcomeUnknown;
    }

    public static ShippingProviderException unsupported(String providerCode, String feature) {
        return new ShippingProviderException(providerCode + "는 " + feature + " 기능을 지원하지 않습니다.",
                null, "UNSUPPORTED", false);
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
