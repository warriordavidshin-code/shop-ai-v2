package com.petitcamel.shop.shipping.provider.goodsflow;

import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.provider.ShippingProviderClient;
import org.springframework.stereotype.Component;

/**
 * 굿스플로 placeholder. Goodsflow's endpoints and auth depend on the merchant contract, so no call is implemented
 * yet and {@link #supports} reports nothing; the registry therefore never selects it. To integrate, implement
 * {@code tracking / issueWaybill / requestPickup / requestReturnPickup / lookup / testConnection}, return true from
 * {@link #supports} for them, and enable the GOODSFLOW row in the admin shipping settings.
 */
@Component
public class GoodsflowShippingClient implements ShippingProviderClient {

    public static final String CODE = "GOODSFLOW";

    private final ShippingProperties properties;

    public GoodsflowShippingClient(ShippingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        String baseUrl = properties.getGoodsflow().getBaseUrl();
        String apiKey = properties.getApiKey();
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean supports(ProviderCapability capability) {
        return false;
    }
}
