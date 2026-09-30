package com.petitcamel.shop.shipping.provider.manual;

import com.petitcamel.shop.shipping.provider.ShippingProvider;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import org.springframework.stereotype.Component;

/** No external API: statuses are changed by admins and only shop-recorded events are shown. */
@Component
public class ManualShippingProvider implements ShippingProvider {

    public static final String NAME = "MANUAL";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public TrackingResponse tracking(String companyCode, String trackingNumber) {
        return TrackingResponse.notFound("외부 배송조회 연동이 설정되지 않았습니다.");
    }
}
