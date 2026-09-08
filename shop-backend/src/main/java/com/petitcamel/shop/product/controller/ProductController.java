package com.petitcamel.shop.product.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.product.domain.ProductSort;
import com.petitcamel.shop.product.dto.ProductDetailResponse;
import com.petitcamel.shop.product.dto.ProductSummaryResponse;
import com.petitcamel.shop.product.service.ProductCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductCatalogService productCatalogService;

    public ProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public PageResponse<ProductSummaryResponse> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductSort sort,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) Boolean availableOnly,
            HttpServletRequest request) {
        // Contract lists page "size" and clothing "size"; parse both from the size query values.
        int pageSize = 20;
        String clothingSize = null;
        String[] sizeValues = request.getParameterValues("size");
        if (sizeValues != null) {
            for (String value : sizeValues) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (isInteger(value)) {
                    pageSize = Integer.parseInt(value);
                } else {
                    clothingSize = value;
                }
            }
        }

        return productCatalogService.listProducts(
                page, pageSize, category, keyword, sort, minPrice, maxPrice, color, clothingSize, availableOnly);
    }

    @GetMapping("/new")
    public List<ProductSummaryResponse> getNewProducts() {
        return productCatalogService.getNewProducts();
    }

    @GetMapping("/best")
    public List<ProductSummaryResponse> getBestProducts() {
        return productCatalogService.getBestProducts();
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse getProduct(@PathVariable Long productId) {
        return productCatalogService.getProduct(productId);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
