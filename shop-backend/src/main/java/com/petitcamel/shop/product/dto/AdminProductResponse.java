package com.petitcamel.shop.product.dto;

import com.petitcamel.shop.product.domain.ImageType;
import com.petitcamel.shop.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminProductResponse(
        Long productId,
        Long categoryId,
        String productName,
        String brandName,
        String summary,
        String description,
        BigDecimal normalPrice,
        BigDecimal salePrice,
        int discountRate,
        ProductStatus status,
        String fitType,
        String material,
        String thickness,
        String stretch,
        String seeThrough,
        String season,
        Instant createdAt,
        Instant updatedAt,
        List<AdminImageResponse> images,
        List<AdminSkuResponse> skus
) {
    public record AdminImageResponse(
            Long imageId,
            String imageUrl,
            ImageType imageType,
            String altText,
            Integer sortOrder
    ) {
    }

    public record AdminSkuResponse(
            Long skuId,
            String skuCode,
            String color,
            String size,
            BigDecimal additionalPrice,
            ProductStatus status,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity
    ) {
    }
}
