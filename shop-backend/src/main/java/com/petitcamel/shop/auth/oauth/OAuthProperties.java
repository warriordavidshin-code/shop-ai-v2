package com.petitcamel.shop.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    /**
     * Browser-facing origin of the shop UI (final post-login redirect target).
     */
    private String frontendUrl = "http://localhost:3000";

    /**
     * Public base used as OAuth redirect_uri prefix.
     * Prefer the Next.js BFF ({frontend}/api/shop) so auth cookies attach to the shop origin.
     */
    private String publicCallbackBase = "http://localhost:3000/api/shop";

    private final Provider kakao = new Provider();
    private final Provider naver = new Provider();

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String getPublicCallbackBase() {
        return publicCallbackBase;
    }

    public void setPublicCallbackBase(String publicCallbackBase) {
        this.publicCallbackBase = publicCallbackBase;
    }

    public Provider getKakao() {
        return kakao;
    }

    public Provider getNaver() {
        return naver;
    }

    public String callbackUrl(String providerPath) {
        String base = publicCallbackBase.endsWith("/")
                ? publicCallbackBase.substring(0, publicCallbackBase.length() - 1)
                : publicCallbackBase;
        return base + "/auth/" + providerPath + "/callback";
    }

    public static class Provider {
        private String clientId = "";
        private String clientSecret = "";
        private boolean enabled = false;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isConfigured() {
            return enabled
                    && clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
