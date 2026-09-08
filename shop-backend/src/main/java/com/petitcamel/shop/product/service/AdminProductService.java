package com.petitcamel.shop.product.service;

import com.petitcamel.shop.category.repository.CategoryRepository;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.product.domain.ImageType;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductImage;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.domain.ProductSort;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.dto.AdminProductRequest;
import com.petitcamel.shop.product.dto.AdminProductResponse;
import com.petitcamel.shop.product.dto.ProductStatusUpdateRequest;
import com.petitcamel.shop.product.repository.ProductImageRepository;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import com.petitcamel.shop.product.util.ProductPricing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public AdminProductService(
            ProductRepository productRepository,
            ProductSkuRepository productSkuRepository,
            ProductImageRepository productImageRepository,
            InventoryRepository inventoryRepository,
            CategoryRepository categoryRepository,
            Clock clock) {
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.productImageRepository = productImageRepository;
        this.inventoryRepository = inventoryRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> listProducts(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Page<Product> products = productRepository.findAll(
                PageRequest.of(safePage, safeSize, ProductSort.RECOMMENDED.toSort()));
        List<AdminProductResponse> content = products.getContent().stream()
                .map(this::toAdminResponse)
                .toList();
        return PageResponse.of(content, products.getNumber(), products.getSize(), products.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminProductResponse getProduct(Long productId) {
        return toAdminResponse(requireProduct(productId));
    }

    @Transactional
    public AdminProductResponse createProduct(AdminProductRequest request) {
        requireCategory(request.categoryId());
        Instant now = clock.instant();

        Product product = new Product();
        applyProductFields(product, request);
        product.setStatus(request.status() == null ? ProductStatus.DRAFT : request.status());
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        Product saved = productRepository.save(product);

        upsertSkus(saved.getProductId(), request.skus(), now, true);
        replaceImages(saved.getProductId(), request.images(), now);
        return toAdminResponse(saved);
    }

    @Transactional
    public AdminProductResponse updateProduct(Long productId, AdminProductRequest request) {
        Product product = requireProduct(productId);
        requireCategory(request.categoryId());
        Instant now = clock.instant();

        applyProductFields(product, request);
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        product.setUpdatedAt(now);
        Product saved = productRepository.save(product);

        if (request.skus() != null) {
            upsertSkus(saved.getProductId(), request.skus(), now, false);
        }
        if (request.images() != null) {
            replaceImages(saved.getProductId(), request.images(), now);
        }
        return toAdminResponse(saved);
    }

    @Transactional
    public AdminProductResponse updateStatus(Long productId, ProductStatusUpdateRequest request) {
        Product product = requireProduct(productId);
        product.setStatus(request.status());
        product.setUpdatedAt(clock.instant());
        return toAdminResponse(productRepository.save(product));
    }

    private void upsertSkus(Long productId, List<AdminProductRequest.SkuRequest> skuRequests, Instant now, boolean creating) {
        if (skuRequests == null) {
            if (creating) {
                return;
            }
            return;
        }

        List<ProductSku> existing = productSkuRepository.findByProductId(productId);
        Map<Long, ProductSku> byId = existing.stream()
                .filter(sku -> sku.getSkuId() != null)
                .collect(Collectors.toMap(ProductSku::getSkuId, sku -> sku));
        Map<String, ProductSku> byCode = existing.stream()
                .collect(Collectors.toMap(ProductSku::getSkuCode, sku -> sku, (a, b) -> a));

        Set<Long> keepIds = new HashSet<>();
        for (AdminProductRequest.SkuRequest skuRequest : skuRequests) {
            validateSkuCodeUnique(skuRequest, productId);

            ProductSku sku = null;
            if (skuRequest.skuId() != null) {
                sku = byId.get(skuRequest.skuId());
                if (sku == null || !sku.getProductId().equals(productId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "SKU를 찾을 수 없습니다.");
                }
            } else if (byCode.containsKey(skuRequest.skuCode())) {
                sku = byCode.get(skuRequest.skuCode());
            }

            if (sku == null) {
                sku = new ProductSku();
                sku.setProductId(productId);
                sku.setCreatedAt(now);
            }

            sku.setSkuCode(skuRequest.skuCode().trim());
            sku.setColor(skuRequest.color().trim());
            sku.setSize(skuRequest.size().trim());
            sku.setAdditionalPrice(
                    skuRequest.additionalPrice() == null ? BigDecimal.ZERO : skuRequest.additionalPrice());
            sku.setStatus(skuRequest.status() == null ? ProductStatus.ON_SALE : skuRequest.status());
            sku.setUpdatedAt(now);
            ProductSku savedSku = productSkuRepository.save(sku);
            keepIds.add(savedSku.getSkuId());

            Inventory inventory = inventoryRepository.findBySkuId(savedSku.getSkuId())
                    .orElseGet(() -> {
                        Inventory created = new Inventory();
                        created.setSkuId(savedSku.getSkuId());
                        created.setReservedQuantity(0);
                        created.setReorderPoint(5);
                        created.setVersion(0L);
                        return created;
                    });
            if (skuRequest.stockQuantity() != null) {
                inventory.setStockQuantity(skuRequest.stockQuantity());
            } else if (inventory.getInventoryId() == null) {
                inventory.setStockQuantity(0);
            }
            inventory.setUpdatedAt(now);
            inventoryRepository.save(inventory);
        }

        for (ProductSku sku : existing) {
            if (!keepIds.contains(sku.getSkuId())) {
                inventoryRepository.findBySkuId(sku.getSkuId()).ifPresent(inventoryRepository::delete);
                productSkuRepository.delete(sku);
            }
        }
    }

    private void validateSkuCodeUnique(AdminProductRequest.SkuRequest skuRequest, Long productId) {
        productSkuRepository.findBySkuCode(skuRequest.skuCode().trim()).ifPresent(existing -> {
            if (skuRequest.skuId() != null) {
                if (!existing.getSkuId().equals(skuRequest.skuId())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 SKU 코드입니다.");
                }
                return;
            }
            if (!existing.getProductId().equals(productId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 SKU 코드입니다.");
            }
        });
    }

    private void applyProductFields(Product product, AdminProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setProductName(request.productName().trim());
        product.setBrandName(request.brandName().trim());
        product.setSummary(request.summary());
        product.setDescription(request.description());
        product.setNormalPrice(request.normalPrice());
        product.setSalePrice(request.salePrice());
        product.setFitType(request.fitType());
        product.setMaterial(request.material());
        product.setThickness(request.thickness());
        product.setStretch(request.stretch());
        product.setSeeThrough(request.seeThrough());
        product.setSeason(request.season());
    }

    private void replaceImages(Long productId, List<AdminProductRequest.ImageRequest> images, Instant now) {
        productImageRepository.deleteByProductId(productId);
        if (images == null || images.isEmpty()) {
            return;
        }
        if (images.size() > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "상품 이미지는 최대 5개까지 등록할 수 있습니다.");
        }
        long mainCount = images.stream().filter(image -> image.imageType() == ImageType.MAIN).count();
        if (mainCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "대표 이미지(MAIN)는 정확히 1개여야 합니다.");
        }
        int order = 0;
        for (AdminProductRequest.ImageRequest imageRequest : images) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setImageUrl(imageRequest.imageUrl().trim());
            image.setImageType(imageRequest.imageType());
            image.setAltText(imageRequest.altText());
            image.setSortOrder(imageRequest.sortOrder() == null ? order : imageRequest.sortOrder());
            image.setCreatedAt(now);
            productImageRepository.save(image);
            order++;
        }
    }

    private AdminProductResponse toAdminResponse(Product product) {
        List<ProductSku> skus = productSkuRepository.findByProductId(product.getProductId());
        Map<Long, Inventory> inventoryBySku = new HashMap<>();
        if (!skus.isEmpty()) {
            for (Inventory inventory : inventoryRepository.findBySkuIdIn(
                    skus.stream().map(ProductSku::getSkuId).toList())) {
                inventoryBySku.put(inventory.getSkuId(), inventory);
            }
        }

        List<AdminProductResponse.AdminSkuResponse> skuResponses = new ArrayList<>();
        for (ProductSku sku : skus) {
            Inventory inventory = inventoryBySku.get(sku.getSkuId());
            int stock = inventory == null ? 0 : inventory.getStockQuantity();
            int reserved = inventory == null ? 0 : inventory.getReservedQuantity();
            skuResponses.add(new AdminProductResponse.AdminSkuResponse(
                    sku.getSkuId(),
                    sku.getSkuCode(),
                    sku.getColor(),
                    sku.getSize(),
                    sku.getAdditionalPrice(),
                    sku.getStatus(),
                    stock,
                    reserved,
                    ProductPricing.availableQuantity(stock, reserved)));
        }

        List<AdminProductResponse.AdminImageResponse> imageResponses =
                productImageRepository.findByProductIdOrderBySortOrderAsc(product.getProductId()).stream()
                        .map(image -> new AdminProductResponse.AdminImageResponse(
                                image.getImageId(),
                                image.getImageUrl(),
                                image.getImageType(),
                                image.getAltText(),
                                image.getSortOrder()))
                        .toList();

        return new AdminProductResponse(
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
                product.getCreatedAt(),
                product.getUpdatedAt(),
                imageResponses,
                skuResponses);
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    private void requireCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "카테고리를 찾을 수 없습니다.");
        }
    }
}
