package com.petitcamel.shop.shipping.dto;

import java.util.List;

/**
 * Return section of the order detail page.
 *
 * @param returnRequest latest return for the order, or null
 * @param canRequest    true when the order is delivered and has no active return
 * @param canCancel     true when the latest return can still be withdrawn (nothing picked up yet)
 */
public record CustomerReturnInfo(
        ReturnRequestResponse returnRequest,
        boolean canRequest,
        boolean canCancel,
        long returnShippingFee,
        List<ReasonOption> reasons
) {

    public record ReasonOption(String code, String label, boolean freeReturn) {
    }
}
