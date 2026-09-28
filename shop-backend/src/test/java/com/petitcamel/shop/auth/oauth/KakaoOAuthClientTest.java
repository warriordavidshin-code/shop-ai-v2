package com.petitcamel.shop.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthClientTest {

    @Test
    void authorizeUrlRequestsRequiredScopesWithoutUnsupportedPrompt() {
        OAuthProperties properties = new OAuthProperties();
        properties.setPublicCallbackBase("https://btc-camel.com/api/shop");
        properties.getKakao().setEnabled(true);
        properties.getKakao().setClientId("client-id");
        properties.getKakao().setClientSecret("client-secret");

        String url = new KakaoOAuthClient(properties).buildAuthorizeUrl("state-1");

        assertThat(url)
                .startsWith("https://kauth.kakao.com/oauth/authorize?")
                .contains("client_id=client-id")
                .contains("redirect_uri=https://btc-camel.com/api/shop/auth/kakao/callback")
                .contains("state=state-1")
                .contains("scope=account_email,name,gender,age_range,birthyear,phone_number,shipping_address")
                .doesNotContain("prompt=");
    }

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
