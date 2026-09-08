package com.petitcamel.shop.product.service;

import com.petitcamel.shop.category.service.CategoryService;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.product.domain.ImageType;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductImage;
import com.petitcamel.shop.product.domain.ProductMeasurement;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.domain.ProductSort;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.dto.ProductDetailResponse;
import com.petitcamel.shop.product.dto.ProductSummaryResponse;
import com.petitcamel.shop.product.repository.ProductImageRepository;
import com.petitcamel.shop.product.repository.ProductMeasurementRepository;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import com.petitcamel.shop.product.repository.ProductSpecifications;
import com.petitcamel.shop.product.util.ProductPricing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductCatalogService {

    private static final int CURATED_LIMIT = 8;

    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMeasurementRepository productMeasurementRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryService categoryService;

    public ProductCatalogService(
            ProductRepository productRepository,
            ProductSkuRepository productSkuRepository,
            ProductImageRepository productImageRepository,
            ProductMeasurementRepository productMeasurementRepository,
            InventoryRepository inventoryRepository,
            CategoryService categoryService) {
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.productImageRepository = productImageRepository;
        this.productMeasurementRepository = productMeasurementRepository;
        this.inventoryRepository = inventoryRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listProducts(
            int page,
            int size,
            String category,
            String keyword,
            ProductSort sort,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String color,
            String sizeFilter,
            Boolean availableOnly) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        ProductSort resolvedSort = sort == null ? ProductSort.RECOMMENDED : sort;

        Specification<Product> spec = Specification
                .where(ProductSpecifications.hasStatus(ProductStatus.ON_SALE))
                .and(ProductSpecifications.inCategories(categoryService.resolveCategoryIds(category)))
                .and(ProductSpecifications.keywordContains(keyword))
                .and(ProductSpecifications.salePriceGte(minPrice))
                .and(ProductSpecifications.salePriceLte(maxPrice))
                .and(ProductSpecifications.hasColor(color))
                .and(ProductSpecifications.hasSize(sizeFilter));

        if (Boolean.TRUE.equals(availableOnly)) {
            spec = spec.and(ProductSpecifications.hasAvailableStock());
        }

        Page<Product> products = productRepository.findAll(
                spec,
                PageRequest.of(safePage, safeSize, resolvedSort.toSort()));

        List<ProductSummaryResponse> content = toSummaries(products.getContent(), false);
        return PageResponse.of(content, products.getNumber(), products.getSize(), products.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(item -> item.getStatus() == ProductStatus.ON_SALE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다."));
        return toDetail(product);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getNewProducts() {
        List<Product> products = productRepository.findByStatusOrderByCreatedAtDesc(
                ProductStatus.ON_SALE,
                PageRequest.of(0, CURATED_LIMIT));
        return toSummaries(products, false);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getBestProducts() {
        List<Product> products = productRepository.findByStatusOrderByProductIdAsc(
                ProductStatus.ON_SALE,
                PageRequest.of(0, CURATED_LIMIT));
        return toSummaries(products, false);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getSummariesByProductIds(Collection<Long> productIds, boolean wishlisted) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        List<Product> ordered = productIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return toSummaries(ordered, wishlisted);
    }

    private List<ProductSummaryResponse> toSummaries(List<Product> products, boolean wishlisted) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getProductId).toList();
        List<ProductSku> skus = productSkuRepository.findByProductIdIn(productIds);
        Map<Long, List<ProductSku>> skusByProduct = skus.stream()
                .collect(Collectors.groupingBy(ProductSku::getProductId));

        Map<Long, Integer> availableBySku = loadAvailableBySku(
                skus.stream().map(ProductSku::getSkuId).toList());

        Map<Long, String> mainImageByProduct = loadMainImages(productIds);
        Map<Long, List<String>> imageUrlsByProduct = loadImageUrls(productIds);

        return products.stream()
                .map(product -> toSummary(
                        product,
                        skusByProduct.getOrDefault(product.getProductId(), List.of()),
                        availableBySku,
                        mainImageByProduct.get(product.getProductId()),
                        imageUrlsByProduct.getOrDefault(product.getProductId(), List.of()),
                        wishlisted))
                .toList();
    }

    private ProductSummaryResponse toSummary(
            Product product,
            List<ProductSku> skus,
            Map<Long, Integer> availableBySku,
            String mainImageUrl,
            List<String> imageUrls,
            boolean wishlisted) {
        Set<String> colors = new HashSet<>();
        boolean anyAvailable = false;
        for (ProductSku sku : skus) {
            colors.add(sku.getColor());
            if (availableBySku.getOrDefault(sku.getSkuId(), 0) > 0) {
                anyAvailable = true;
            }
        }
        boolean soldOut = product.getStatus() == ProductStatus.SOLD_OUT || !anyAvailable;
        return new ProductSummaryResponse(
                product.getProductId(),
                product.getProductName(),
                product.getBrandName(),
                product.getNormalPrice(),
                product.getSalePrice(),
                ProductPricing.discountRate(product.getNormalPrice(), product.getSalePrice()),
                mainImageUrl,
                imageUrls,
                colors.size(),
                soldOut,
                wishlisted);
    }

    private ProductDetailResponse toDetail(Product product) {
        List<ProductSku> skus = productSkuRepository.findByProductId(product.getProductId());
        Map<Long, Integer> availableBySku = loadAvailableBySku(
                skus.stream().map(ProductSku::getSkuId).toList());

        boolean anyAvailable = skus.stream()
                .anyMatch(sku -> availableBySku.getOrDefault(sku.getSkuId(), 0) > 0);
        boolean soldOut = product.getStatus() == ProductStatus.SOLD_OUT || !anyAvailable;

        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getProductId());
        List<ProductMeasurement> measurements =
                productMeasurementRepository.findByProductIdOrderBySizeAsc(product.getProductId());

        return new ProductDetailResponse(
                product.getProductId(),
                product.getCategoryId(),
                product.getProductName(),
                product.getBrandName(),
                product.getSummary(),
                product.getDescription(),
                product.getNormalPrice(),
                product.getSalePrice(),
                ProductPricing.discountRate(product.getNormalPrice(), product.getSalePrice()),
                product.getStatus(),
                product.getFitType(),
                product.getMaterial(),
                product.getThickness(),
                product.getStretch(),
                product.getSeeThrough(),
                product.getSeason(),
                soldOut,
                false,
                images.stream()
                        .map(image -> new ProductDetailResponse.ProductImageResponse(
                                image.getImageId(),
                                image.getImageUrl(),
                                image.getImageType(),
                                image.getAltText(),
                                image.getSortOrder()))
                        .toList(),
                skus.stream()
                        .map(sku -> new ProductDetailResponse.ProductSkuResponse(
                                sku.getSkuId(),
                                sku.getSkuCode(),
                                sku.getColor(),
                                sku.getSize(),
                                sku.getAdditionalPrice(),
                                sku.getStatus(),
                                availableBySku.getOrDefault(sku.getSkuId(), 0)))
                        .toList(),
                measurements.stream()
                        .map(item -> new ProductDetailResponse.ProductMeasurementResponse(
                                item.getMeasurementId(),
                                item.getSize(),
                                item.getShoulder(),
                                item.getChest(),
                                item.getWaist(),
                                item.getHip(),
                                item.getSleeve(),
                                item.getTotalLength(),
                                item.getRise(),
                                item.getThigh(),
                                item.getHem()))
                        .toList());
    }

    private Map<Long, Integer> loadAvailableBySku(Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> available = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findBySkuIdIn(skuIds)) {
            available.put(
                    inventory.getSkuId(),
                    ProductPricing.availableQuantity(
                            inventory.getStockQuantity(),
                            inventory.getReservedQuantity()));
        }
        return available;
    }

    private Map<Long, String> loadMainImages(Collection<Long> productIds) {
        Map<Long, String> mainImages = new HashMap<>();
        for (ProductImage image : productImageRepository.findByProductIdIn(productIds)) {
            if (image.getImageType() != ImageType.MAIN) {
                continue;
            }
            mainImages.putIfAbsent(image.getProductId(), image.getImageUrl());
        }
        return mainImages;
    }

    private Map<Long, List<String>> loadImageUrls(Collection<Long> productIds) {
        Map<Long, List<ProductImage>> grouped = new HashMap<>();
        for (ProductImage image : productImageRepository.findByProductIdIn(productIds)) {
            if (image.getImageUrl() == null || image.getImageUrl().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(image.getProductId(), key -> new ArrayList<>()).add(image);
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (Map.Entry<Long, List<ProductImage>> entry : grouped.entrySet()) {
            List<ProductImage> ordered = entry.getValue().stream()
                    .sorted((a, b) -> {
                        boolean aMain = a.getImageType() == ImageType.MAIN;
                        boolean bMain = b.getImageType() == ImageType.MAIN;
                        if (aMain != bMain) {
                            return aMain ? -1 : 1;
                        }
                        return Integer.compare(
                                a.getSortOrder() == null ? 0 : a.getSortOrder(),
                                b.getSortOrder() == null ? 0 : b.getSortOrder());
                    })
                    .toList();
            result.put(entry.getKey(), ordered.stream().map(ProductImage::getImageUrl).toList());
        }
        return result;
    }
}
