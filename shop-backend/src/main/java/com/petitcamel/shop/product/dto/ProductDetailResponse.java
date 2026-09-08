package com.petitcamel.shop.product.dto;

import com.petitcamel.shop.product.domain.ImageType;
import com.petitcamel.shop.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailResponse(
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
        boolean soldOut,
        boolean wishlisted,
        List<ProductImageResponse> images,
        List<ProductSkuResponse> skus,
        List<ProductMeasurementResponse> measurements
) {
    public record ProductImageResponse(
            Long imageId,
            String imageUrl,
            ImageType imageType,
            String altText,
            int sortOrder
    ) {
    }

    public record ProductSkuResponse(
            Long skuId,
            String skuCode,
            String color,
            String size,
            BigDecimal additionalPrice,
            ProductStatus status,
            int availableQuantity
    ) {
    }

    public record ProductMeasurementResponse(
            Long measurementId,
            String size,
            BigDecimal shoulder,
            BigDecimal chest,
            BigDecimal waist,
            BigDecimal hip,
            BigDecimal sleeve,
            BigDecimal totalLength,
            BigDecimal rise,
            BigDecimal thigh,
            BigDecimal hem
    ) {
    }
}
