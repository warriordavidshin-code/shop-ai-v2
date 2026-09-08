package com.petitcamel.shop.product.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProductPricing {

    private ProductPricing() {
    }

    /**
     * discountRate = round((normal - sale) / normal * 100) when normal &gt; 0; otherwise 0.
     */
    public static int discountRate(BigDecimal normalPrice, BigDecimal salePrice) {
        if (normalPrice == null || normalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal sale = salePrice == null ? BigDecimal.ZERO : salePrice;
        return normalPrice.subtract(sale)
                .multiply(BigDecimal.valueOf(100))
                .divide(normalPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public static int availableQuantity(int stockQuantity, int reservedQuantity) {
        return Math.max(0, stockQuantity - reservedQuantity);
    }
}
