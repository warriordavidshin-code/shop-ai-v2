package com.petitcamel.shop.shipping.provider.goodsflow;

import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.provider.ShippingProvider;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import org.springframework.stereotype.Component;

/**
 * 굿스플로 placeholder. Goodsflow requires a contract-specific endpoint and auth scheme, so the call is
 * not implemented yet; it stays unselectable ({@link #isConfigured()} false) until the endpoint is set
 * and {@link #tracking} is filled in.
 */
@Component
public class GoodsflowShippingProvider implements ShippingProvider {

    public static final String NAME = "GOODSFLOW";

    private final ShippingProperties properties;

    public GoodsflowShippingProvider(ShippingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isConfigured() {
        String baseUrl = properties.getGoodsflow().getBaseUrl();
        String apiKey = properties.getApiKey();
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public TrackingResponse tracking(String companyCode, String trackingNumber) {
        throw new ShippingProviderException("굿스플로 배송조회 연동이 아직 구현되지 않았습니다.");
    }
}
