package com.petitcamel.shop.shipping.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingLogMaskerTest {

    @Test
    void masksAllButFirstFourCharacters() {
        assertThat(ShippingLogMasker.maskTrackingNumber("1234567890")).isEqualTo("1234******");
        assertThat(ShippingLogMasker.maskTrackingNumber("123")).isEqualTo("***");
        assertThat(ShippingLogMasker.maskTrackingNumber(null)).isEqualTo("-");
    }

    @Test
    void scrubsSecretAndTruncates() {
        String scrubbed = ShippingLogMasker.scrub("GET https://x/api?t_key=SECRET123&t_code=05 failed", "SECRET123");
        assertThat(scrubbed).doesNotContain("SECRET123").contains("t_key=****");
        assertThat(ShippingLogMasker.scrub("x".repeat(500), null)).hasSize(200);
    }
}
