package com.petitcamel.shop.product.domain;

import org.springframework.data.domain.Sort;

public enum ProductSort {
    RECOMMENDED,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC;

    public Sort toSort() {
        return switch (this) {
            case RECOMMENDED -> Sort.by(Sort.Direction.DESC, "productId");
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "salePrice");
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "salePrice");
        };
    }
}
