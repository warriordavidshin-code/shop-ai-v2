package com.petitcamel.shop.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthClientTest {

    @Test
    void normalizesKakaoPhoneNumber() {
        assertThat(KakaoOAuthClient.normalizeKakaoPhone("+82 10-1234-5678")).isEqualTo("01012345678");
        assertThat(KakaoOAuthClient.normalizeKakaoPhone("010-9999-8888")).isEqualTo("01099998888");
        assertThat(KakaoOAuthClient.normalizeKakaoPhone(null)).isNull();
    }

    @Test
    void shippingAddressIsPresentWhenBaseAddressExists() {
        KakaoOAuthClient.ShippingAddress address = new KakaoOAuthClient.ShippingAddress(
                "홍길동",
                "01012345678",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101동");
        assertThat(address.isPresent()).isTrue();
    }

    @Test
    void shippingAddressIsMissingWhenEmpty() {
        assertThat(KakaoOAuthClient.ShippingAddress.empty().isPresent()).isFalse();
    }
}
