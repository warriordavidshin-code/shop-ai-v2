package com.petitcamel.shop.shipping.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Return section of the order detail page.
 *
 * @param returnRequest latest return for the order, or null
 * @param canRequest    true when the order is delivered and has no active return
 */
public record CustomerReturnInfo(
        ReturnRequestResponse returnRequest,
        boolean canRequest,
        BigDecimal returnShippingFee,
        List<ReasonOption> reasons
) {

    public record ReasonOption(String code, String label, boolean freeReturn) {
    }
}
