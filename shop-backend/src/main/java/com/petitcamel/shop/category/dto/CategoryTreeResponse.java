package com.petitcamel.shop.category.dto;

import java.util.List;

public record CategoryTreeResponse(
        Long categoryId,
        String name,
        String slug,
        List<CategoryTreeResponse> children
) {
}
