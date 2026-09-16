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
}
