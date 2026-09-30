package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.ShippingOperationType;

import java.util.Optional;

/**
 * Java client for one external shipping API vendor. {@link #code()} matches {@code shipping_provider.code}.
 * A capability is used only when the client {@link #supports} it, is {@link #isConfigured() configured}, and the
 * operator enabled it on the {@code shipping_provider} row. Unsupported operations throw.
 */
public interface ShippingProviderClient {

    String code();

    /** False when required settings (API key, endpoint) are missing. */
    boolean isConfigured();

    /** Whether this client implements the capability (independent of configuration or operator switches). */
    boolean supports(ProviderCapability capability);

    /**
     * @param externalCompanyCode courier code in this vendor's code system
     * @throws ShippingProviderException when the vendor cannot be reached or rejects the request
     */
    default TrackingResponse tracking(String externalCompanyCode, String trackingNumber) {
        throw ShippingProviderException.unsupported(code(), "배송조회");
    }

    default ProviderActionResult issueWaybill(ShipmentCommand command) {
        throw ShippingProviderException.unsupported(code(), "송장발급");
    }

    default ProviderActionResult requestPickup(ShipmentCommand command) {
        throw ShippingProviderException.unsupported(code(), "집하요청");
    }

    default ProviderActionResult requestReturnPickup(ShipmentCommand command) {
        throw ShippingProviderException.unsupported(code(), "반품수거");
    }

    /** Waybill label for an issued invoice (read-only; not logged as an operation). */
    default ProviderActionResult printWaybill(String externalReference, String trackingNumber) {
        throw ShippingProviderException.unsupported(code(), "송장출력");
    }

    /**
     * Looks up an earlier state-changing call by our {@code requestId} after its result was lost (crash between
     * the vendor call and saving the result). Empty when the vendor has no such lookup or no record.
     */
    default Optional<ProviderActionResult> lookup(ShippingOperationType type, String requestId) {
        return Optional.empty();
    }

    /** Cheap authenticated call used by the admin "연결 테스트" button. */
    default String testConnection() {
        throw ShippingProviderException.unsupported(code(), "연결 테스트");
    }
}
