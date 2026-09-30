package com.petitcamel.shop.shipping.waybill;

/**
 * 송장 발급/출력. Needs an integration such as Goodsflow; until then {@link UnsupportedWaybillService}
 * reports it as unavailable and admins register invoice numbers by hand.
 */
public interface WaybillService {

    boolean isSupported();

    WaybillResponse issue(WaybillRequest request);

    WaybillResponse print(WaybillRequest request);
}
