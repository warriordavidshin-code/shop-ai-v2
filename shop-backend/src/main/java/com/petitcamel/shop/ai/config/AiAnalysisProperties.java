package com.petitcamel.shop.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * {@code app.ai.analysis.*}: receipt / product image analysis through the OpenAI Responses API.
 * The API key comes from the OPENAI_API_KEY environment variable only; it is never logged,
 * stored in the database or returned to the browser.
 */
@ConfigurationProperties(prefix = "app.ai.analysis")
public class AiAnalysisProperties {

    private boolean receiptEnabled = true;
    private boolean productEnabled = true;
    private boolean mockEnabled = false;
    private String apiKey = "";
    private String model = "";
    private String defaultModel = "gpt-4.1-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private int connectTimeoutSeconds = 10;
    private int timeoutSeconds = 90;
    private int maxRetries = 2;
    private long retryBackoffMs = 1500;
    private int maxOutputTokens = 4000;
    private BigDecimal inputPricePerMillion = new BigDecimal("0.40");
    private BigDecimal outputPricePerMillion = new BigDecimal("1.60");
    private int cacheDays = 30;
    private int receiptMaxFiles = 10;
    private int productMaxImages = 8;
    private long maxFileBytes = 10L * 1024 * 1024;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** True when analysis can run at all: a real key, or mock mode. */
    public boolean isAvailable() {
        return mockEnabled || hasApiKey();
    }

    public String effectiveModel() {
        return model == null || model.isBlank() ? defaultModel : model.trim();
    }

    public boolean isReceiptEnabled() {
        return receiptEnabled;
    }

    public void setReceiptEnabled(boolean receiptEnabled) {
        this.receiptEnabled = receiptEnabled;
    }

    public boolean isProductEnabled() {
        return productEnabled;
    }

    public void setProductEnabled(boolean productEnabled) {
        this.productEnabled = productEnabled;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public BigDecimal getInputPricePerMillion() {
        return inputPricePerMillion;
    }

    public void setInputPricePerMillion(BigDecimal inputPricePerMillion) {
        this.inputPricePerMillion = inputPricePerMillion;
    }

    public BigDecimal getOutputPricePerMillion() {
        return outputPricePerMillion;
    }

    public void setOutputPricePerMillion(BigDecimal outputPricePerMillion) {
        this.outputPricePerMillion = outputPricePerMillion;
    }

    public int getCacheDays() {
        return cacheDays;
    }

    public void setCacheDays(int cacheDays) {
        this.cacheDays = cacheDays;
    }

    public int getReceiptMaxFiles() {
        return receiptMaxFiles;
    }

    public void setReceiptMaxFiles(int receiptMaxFiles) {
        this.receiptMaxFiles = receiptMaxFiles;
    }

    public int getProductMaxImages() {
        return productMaxImages;
    }

    public void setProductMaxImages(int productMaxImages) {
        this.productMaxImages = productMaxImages;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }
}
