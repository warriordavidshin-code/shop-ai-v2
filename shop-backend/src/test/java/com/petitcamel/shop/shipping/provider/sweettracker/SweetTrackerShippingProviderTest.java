package com.petitcamel.shop.shipping.provider.sweettracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SweetTrackerShippingProviderTest {

    private final ShippingProperties properties = new ShippingProperties();
    private final SweetTrackerShippingProvider provider =
            new SweetTrackerShippingProvider(properties, new ObjectMapper());

    @Test
    void notConfiguredWithoutApiKey() {
        assertThat(provider.isConfigured()).isFalse();
        assertThatThrownBy(() -> provider.tracking("05", "123456789012"))
                .isInstanceOf(ShippingProviderException.class);
        properties.setApiKey("test-key");
        assertThat(provider.isConfigured()).isTrue();
    }

    @Test
    void parsesTrackingDetailsInTimeOrder() {
        TrackingResponse response = provider.parse("""
                {
                  "complete": false,
                  "level": 3,
                  "trackingDetails": [
                    {"timeString":"2026-09-29 13:10:00","where":"대전HUB","kind":"간선상차","level":3},
                    {"timeString":"2026-09-29 09:00:00","where":"서울강남","kind":"집화처리","level":2}
                  ]
                }
                """);

        assertThat(response.found()).isTrue();
        assertThat(response.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(response.events()).hasSize(2);
        assertThat(response.events().get(0).description()).isEqualTo("집화처리");
        assertThat(response.events().get(0).status()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(response.events().get(0).time()).isEqualTo(Instant.parse("2026-09-29T00:00:00Z"));
        assertThat(response.events().get(1).location()).isEqualTo("대전HUB");
    }

    @Test
    void completeMeansDelivered() {
        TrackingResponse response = provider.parse("""
                {"complete": true, "level": 6, "trackingDetails": [
                  {"time": 1790000000000, "where":"강남", "kind":"배달완료", "level":6}
                ]}
                """);
        assertThat(response.status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(response.events().get(0).time()).isEqualTo(Instant.ofEpochMilli(1790000000000L));
    }

    @Test
    void unknownInvoiceIsNotFound() {
        TrackingResponse response = provider.parse("{\"status\":false,\"msg\":\"운송장 미등록\"}");
        assertThat(response.found()).isFalse();
        assertThat(response.message()).isEqualTo("운송장 미등록");
    }

    @Test
    void authErrorAndGarbageThrow() {
        assertThatThrownBy(() -> provider.parse("{\"status\":false,\"code\":\"101\",\"msg\":\"invalid key\"}"))
                .isInstanceOf(ShippingProviderException.class);
        assertThatThrownBy(() -> provider.parse("<html>"))
                .isInstanceOf(ShippingProviderException.class);
    }
}
