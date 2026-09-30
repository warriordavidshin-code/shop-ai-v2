package com.petitcamel.shop.shipping.pickup;

import org.springframework.stereotype.Service;

@Service
public class ManualPickupService implements PickupService {

    @Override
    public String name() {
        return "MANUAL";
    }

    @Override
    public PickupResponse requestPickup(PickupRequest request) {
        return new PickupResponse(true, false, null,
                "수거요청 상태로 변경했습니다. 택배사에 직접 방문수거를 접수한 뒤 송장번호를 등록해 주세요.");
    }
}
