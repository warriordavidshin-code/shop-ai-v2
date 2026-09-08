package com.petitcamel.shop.product.dto;

import com.petitcamel.shop.product.domain.ImageType;
import com.petitcamel.shop.product.domain.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

        @Valid List<SkuRequest> skus
) {
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

            Integer stockQuantity
    ) {
    }
}
