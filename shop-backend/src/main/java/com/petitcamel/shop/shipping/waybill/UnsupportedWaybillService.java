package com.petitcamel.shop.shipping.waybill;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class UnsupportedWaybillService implements WaybillService {

    private static final String MESSAGE = "송장 발급/출력은 굿스플로 등 송장 연동을 설정한 뒤 사용할 수 있습니다.";

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public WaybillResponse issue(WaybillRequest request) {
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, MESSAGE);
    }

    @Override
    public WaybillResponse print(WaybillRequest request) {
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, MESSAGE);
    }
}
