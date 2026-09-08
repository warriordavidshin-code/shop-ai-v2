package com.petitcamel.shop.product.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.product.dto.AdminProductRequest;
import com.petitcamel.shop.product.dto.AdminProductResponse;
import com.petitcamel.shop.product.dto.ProductStatusUpdateRequest;
import com.petitcamel.shop.product.service.AdminProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @GetMapping
    public PageResponse<AdminProductResponse> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminProductService.listProducts(page, size);
    }

    @GetMapping("/{productId}")
    public AdminProductResponse getProduct(@PathVariable Long productId) {
        return adminProductService.getProduct(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse createProduct(@Valid @RequestBody AdminProductRequest request) {
        return adminProductService.createProduct(request);
    }

    @PutMapping("/{productId}")
    public AdminProductResponse updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductRequest request) {
        return adminProductService.updateProduct(productId, request);
    }

    @PatchMapping("/{productId}/status")
    public AdminProductResponse updateStatus(
            @PathVariable Long productId,
            @Valid @RequestBody ProductStatusUpdateRequest request) {
        return adminProductService.updateStatus(productId, request);
    }
}
