package com.petitcamel.shop.product.dto;

import com.petitcamel.shop.product.domain.ImageType;
import com.petitcamel.shop.product.domain.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductRequest(
        @NotNull Long categoryId,

        @NotBlank @Size(max = 200) String productName,

        @NotBlank @Size(max = 100) String brandName,

        @Size(max = 500) String summary,

        String description,

        @NotNull @DecimalMin("0") BigDecimal normalPrice,

        @NotNull @DecimalMin("0") BigDecimal salePrice,

        ProductStatus status,

        @Size(max = 50) String fitType,

        @Size(max = 100) String material,

        @Size(max = 50) String thickness,

        @Size(max = 50) String stretch,

        @Size(max = 50) String seeThrough,

        @Size(max = 50) String season,

        @Valid @Size(max = 5) List<ImageRequest> images,

        @Valid List<SkuRequest> skus,

        @Size(max = 500) String searchKeywords,

        @Size(max = 300) String seoDescription,

        @DecimalMin("0") BigDecimal purchasePrice
) {
    /** Constructor kept for callers that predate the AI / cost fields. */
    public AdminProductRequest(
            Long categoryId, String productName, String brandName, String summary, String description,
            BigDecimal normalPrice, BigDecimal salePrice, ProductStatus status, String fitType, String material,
            String thickness, String stretch, String seeThrough, String season, List<ImageRequest> images,
            List<SkuRequest> skus) {
        this(categoryId, productName, brandName, summary, description, normalPrice, salePrice, status, fitType,
                material, thickness, stretch, seeThrough, season, images, skus, null, null, null);
    }

    public record ImageRequest(
            @NotBlank @Size(max = 500) String imageUrl,

            @NotNull ImageType imageType,

            @Size(max = 255) String altText,

            Integer sortOrder
    ) {
    }

    public record SkuRequest(
            Long skuId,

            @NotBlank @Size(max = 64) String skuCode,

            @NotBlank @Size(max = 50) String color,

            @NotBlank @Size(max = 30) String size,

            @DecimalMin("0") BigDecimal additionalPrice,

            ProductStatus status,

            @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.") Integer stockQuantity
    ) {
    }
}
