package com.petitcamel.shop.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.category.domain.Category;
import com.petitcamel.shop.category.repository.CategoryRepository;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.dto.ProductSummaryResponse;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import com.petitcamel.shop.product.service.ProductCatalogService;
import com.petitcamel.shop.product.util.ProductPricing;
import com.petitcamel.shop.recommendation.domain.AiRecommendation;
import com.petitcamel.shop.recommendation.domain.RecommendationEvent;
import com.petitcamel.shop.recommendation.dto.OutfitRecommendationRequest;
import com.petitcamel.shop.recommendation.dto.OutfitRecommendationResponse;
import com.petitcamel.shop.recommendation.dto.RecommendationEventRequest;
import com.petitcamel.shop.recommendation.provider.OutfitCandidate;
import com.petitcamel.shop.recommendation.provider.OutfitRecommendationContext;
import com.petitcamel.shop.recommendation.provider.OutfitRecommendationProvider;
import com.petitcamel.shop.recommendation.provider.OutfitRecommendationResult;
import com.petitcamel.shop.recommendation.repository.AiRecommendationRepository;
import com.petitcamel.shop.recommendation.repository.RecommendationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OutfitRecommendationService {

    private final OutfitRecommendationProvider outfitRecommendationProvider;
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCatalogService productCatalogService;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final RecommendationEventRepository recommendationEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutfitRecommendationService(
            OutfitRecommendationProvider outfitRecommendationProvider,
            ProductRepository productRepository,
            ProductSkuRepository productSkuRepository,
            InventoryRepository inventoryRepository,
            CategoryRepository categoryRepository,
            ProductCatalogService productCatalogService,
            AiRecommendationRepository aiRecommendationRepository,
            RecommendationEventRepository recommendationEventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.outfitRecommendationProvider = outfitRecommendationProvider;
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.inventoryRepository = inventoryRepository;
        this.categoryRepository = categoryRepository;
        this.productCatalogService = productCatalogService;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.recommendationEventRepository = recommendationEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public OutfitRecommendationResponse recommend(Long memberId, OutfitRecommendationRequest request) {
        List<OutfitCandidate> candidates = loadInStockOnSaleCandidates();
        OutfitRecommendationContext context = new OutfitRecommendationContext(
                request.occasion(),
                request.style(),
                request.colors() == null ? List.of() : request.colors(),
                request.budget(),
                candidates);

        OutfitRecommendationResult result = outfitRecommendationProvider.recommend(context);
        Set<Long> allowedIds = candidates.stream()
                .map(OutfitCandidate::productId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<OutfitRecommendationResponse.Outfit> outfits = new ArrayList<>();
        for (OutfitRecommendationResult.Outfit outfit : result.outfits()) {
            List<Long> productIds = outfit.productIds().stream()
                    .filter(allowedIds::contains)
                    .distinct()
                    .toList();
            if (productIds.isEmpty()) {
                continue;
            }
            List<ProductSummaryResponse> items =
                    productCatalogService.getSummariesByProductIds(productIds, false);
            // Preserve order from recommendation
            Map<Long, ProductSummaryResponse> byId = items.stream()
                    .collect(Collectors.toMap(ProductSummaryResponse::productId, Function.identity()));
            List<ProductSummaryResponse> ordered = productIds.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .filter(p -> !p.soldOut())
                    .toList();
            if (!ordered.isEmpty()) {
                outfits.add(new OutfitRecommendationResponse.Outfit(ordered));
            }
        }

        AiRecommendation entity = new AiRecommendation();
        entity.setMemberId(memberId);
        entity.setRecommendationType("OUTFIT");
        entity.setInputData(toInputJson(request));
        entity.setResultData(toResultJson(result.provider(), outfits));
        entity.setProvider(result.provider());
        entity.setModelName(result.modelName());
        entity.setCreatedAt(clock.instant());
        AiRecommendation saved = aiRecommendationRepository.save(entity);

        return new OutfitRecommendationResponse(saved.getRecommendationId(), saved.getProvider(), outfits);
    }

    @Transactional
    public void recordEvent(Long recommendationId, RecommendationEventRequest request) {
        if (!aiRecommendationRepository.existsById(recommendationId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "추천 결과를 찾을 수 없습니다.");
        }
        if (request.productId() != null && !productRepository.existsById(request.productId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
        RecommendationEvent event = new RecommendationEvent();
        event.setRecommendationId(recommendationId);
        event.setProductId(request.productId());
        event.setEventType(request.eventType());
        event.setCreatedAt(clock.instant());
        recommendationEventRepository.save(event);
    }

    private List<OutfitCandidate> loadInStockOnSaleCandidates() {
        List<Product> products = productRepository.findByStatus(ProductStatus.ON_SALE);
        if (products.isEmpty()) {
            return List.of();
        }

        Map<Long, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));

        List<Long> productIds = products.stream().map(Product::getProductId).toList();
        List<ProductSku> skus = productSkuRepository.findByProductIdIn(productIds).stream()
                .filter(sku -> sku.getStatus() == ProductStatus.ON_SALE)
                .toList();
        Map<Long, List<ProductSku>> skusByProduct = skus.stream()
                .collect(Collectors.groupingBy(ProductSku::getProductId));

        Map<Long, Integer> availableBySku = new HashMap<>();
        List<Long> skuIds = skus.stream().map(ProductSku::getSkuId).toList();
        if (!skuIds.isEmpty()) {
            for (Inventory inventory : inventoryRepository.findBySkuIdIn(skuIds)) {
                availableBySku.put(
                        inventory.getSkuId(),
                        ProductPricing.availableQuantity(
                                inventory.getStockQuantity(), inventory.getReservedQuantity()));
            }
        }

        List<OutfitCandidate> candidates = new ArrayList<>();
        for (Product product : products) {
            List<ProductSku> productSkus = skusByProduct.getOrDefault(product.getProductId(), List.of());
            int available = productSkus.stream()
                    .mapToInt(sku -> availableBySku.getOrDefault(sku.getSkuId(), 0))
                    .sum();
            if (available <= 0) {
                continue;
            }
            List<String> colors = productSkus.stream()
                    .map(ProductSku::getColor)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Category category = categories.get(product.getCategoryId());
            candidates.add(new OutfitCandidate(
                    product.getProductId(),
                    product.getCategoryId(),
                    product.getProductName(),
                    category == null ? "" : category.getSlug(),
                    product.getSalePrice(),
                    colors,
                    available));
        }
        return candidates;
    }

    private JsonNode toInputJson(OutfitRecommendationRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("occasion", request.occasion());
        node.put("style", request.style());
        ArrayNode colors = node.putArray("colors");
        if (request.colors() != null) {
            request.colors().forEach(colors::add);
        }
        if (request.budget() != null) {
            node.put("budget", request.budget());
        } else {
            node.putNull("budget");
        }
        return node;
    }

    private JsonNode toResultJson(String provider, List<OutfitRecommendationResponse.Outfit> outfits) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("provider", provider);
        ArrayNode outfitsNode = node.putArray("outfits");
        for (OutfitRecommendationResponse.Outfit outfit : outfits) {
            ObjectNode outfitNode = outfitsNode.addObject();
            ArrayNode items = outfitNode.putArray("productIds");
            outfit.items().forEach(item -> items.add(item.productId()));
        }
        return node;
    }
}
