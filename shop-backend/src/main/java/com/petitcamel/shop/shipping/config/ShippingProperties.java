package com.petitcamel.shop.shipping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code shipping.*} settings. Secrets (api-key, client-id) come from environment variables only
 * (SHIPPING_API_KEY / SHIPPING_CLIENT_ID) and must never be logged.
 */
@ConfigurationProperties(prefix = "shipping")
public class ShippingProperties {

    /** SWEETTRACKER, GOODSFLOW or MANUAL. Falls back to MANUAL when the chosen provider is not configured. */
    private String provider = "MANUAL";
    private String apiKey = "";
    private String clientId = "";
    private final SweetTracker sweettracker = new SweetTracker();
    private final Goodsflow goodsflow = new Goodsflow();
    private final Tracking tracking = new Tracking();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public SweetTracker getSweettracker() {
        return sweettracker;
    }

    public Goodsflow getGoodsflow() {
        return goodsflow;
    }

    public Tracking getTracking() {
        return tracking;
    }

    public static class SweetTracker {
        private String baseUrl = "https://info.sweettracker.co.kr";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Goodsflow {
        private String baseUrl = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Tracking {
        /** A shipment checked within this window is served from the DB instead of calling the provider. */
        private int cacheMinutes = 25;
        /** Minimum gap between admin-forced refreshes of the same shipment. */
        private int forceRefreshMinSeconds = 60;
        private boolean schedulerEnabled = true;
        private String cron = "0 */30 * * * *";
        private int batchSize = 200;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;

        public int getCacheMinutes() {
            return cacheMinutes;
        }

        public void setCacheMinutes(int cacheMinutes) {
            this.cacheMinutes = cacheMinutes;
        }

        public int getForceRefreshMinSeconds() {
            return forceRefreshMinSeconds;
        }

        public void setForceRefreshMinSeconds(int forceRefreshMinSeconds) {
            this.forceRefreshMinSeconds = forceRefreshMinSeconds;
        }

        public boolean isSchedulerEnabled() {
            return schedulerEnabled;
        }

        public void setSchedulerEnabled(boolean schedulerEnabled) {
            this.schedulerEnabled = schedulerEnabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
