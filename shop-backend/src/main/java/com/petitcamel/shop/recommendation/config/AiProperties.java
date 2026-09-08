package com.petitcamel.shop.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String provider = "rule";
    private final OpenAi openai = new OpenAi();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public boolean isOpenAiSelected() {
        return "openai".equalsIgnoreCase(provider) && hasOpenAiKey();
    }

    public boolean hasOpenAiKey() {
        String key = openai.getApiKey();
        return key != null && !key.isBlank();
    }

    public static class OpenAi {
        private String apiKey = "";
        private String model = "";

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
    }
}
