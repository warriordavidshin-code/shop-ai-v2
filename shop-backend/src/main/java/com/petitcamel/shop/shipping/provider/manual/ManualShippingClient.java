package com.petitcamel.shop.shipping.provider.manual;

import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.ShippingProvider;
import com.petitcamel.shop.shipping.provider.ProviderActionResult;
import com.petitcamel.shop.shipping.provider.ShipmentCommand;
import com.petitcamel.shop.shipping.provider.ShippingProviderClient;
import org.springframework.stereotype.Component;

/**
 * No external API: an admin books pickups with the courier by phone / courier site and records the invoice here.
 * Nothing is sent anywhere, so these calls are not logged as API operations.
 */
@Component
public class ManualShippingClient implements ShippingProviderClient {

    public static final String CODE = ShippingProvider.MANUAL;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean supports(ProviderCapability capability) {
        return capability == ProviderCapability.PICKUP || capability == ProviderCapability.RETURN_PICKUP;
    }

    @Override
    public ProviderActionResult requestPickup(ShipmentCommand command) {
        return new ProviderActionResult(false, null, null, null,
                "집하요청 상태로 저장했습니다. 택배사에 방문 집하를 접수한 뒤 송장번호를 등록해 주세요.");
    }

    @Override
    public ProviderActionResult requestReturnPickup(ShipmentCommand command) {
        return new ProviderActionResult(false, null, null, null,
                "반품수거요청 상태로 저장했습니다. 택배사에 반품 수거를 접수한 뒤 송장번호를 등록해 주세요.");
    }
}
