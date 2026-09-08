package com.petitcamel.shop.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductSummaryResponse(
        Long productId,
        String productName,
        String brandName,
        BigDecimal normalPrice,
        BigDecimal salePrice,
        int discountRate,
        String mainImageUrl,
        List<String> imageUrls,
        int colorCount,
        boolean soldOut,
        boolean wishlisted
) {
}
