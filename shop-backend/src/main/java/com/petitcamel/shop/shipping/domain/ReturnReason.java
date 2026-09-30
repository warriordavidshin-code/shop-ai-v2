package com.petitcamel.shop.shipping.domain;

/** Customer-selectable return reasons. Seller-fault reasons are returned free of charge. */
public enum ReturnReason {
    SIZE_MISMATCH("사이즈가 맞지 않음", false),
    NOT_SATISFIED("상품이 마음에 들지 않음", false),
    DEFECTIVE("상품 불량", true),
    WRONG_DELIVERY("오배송", true),
    CHANGE_OF_MIND("단순 변심", false),
    OTHER("기타", false);

    private final String label;
    private final boolean freeReturn;

    ReturnReason(String label, boolean freeReturn) {
        this.label = label;
        this.freeReturn = freeReturn;
    }

    public String getLabel() {
        return label;
    }

    public boolean isFreeReturn() {
        return freeReturn;
    }
}
