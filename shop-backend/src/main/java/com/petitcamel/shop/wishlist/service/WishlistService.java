package com.petitcamel.shop.wishlist.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.product.dto.ProductSummaryResponse;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.service.ProductCatalogService;
import com.petitcamel.shop.wishlist.domain.Wishlist;
import com.petitcamel.shop.wishlist.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final ProductCatalogService productCatalogService;
    private final Clock clock;

    public WishlistService(
            WishlistRepository wishlistRepository,
            ProductRepository productRepository,
            ProductCatalogService productCatalogService,
            Clock clock) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.productCatalogService = productCatalogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> list(Long memberId) {
        List<Wishlist> entries = wishlistRepository.findByMemberId(memberId).stream()
                .sorted(Comparator.comparing(Wishlist::getCreatedAt).reversed())
                .toList();
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = entries.stream().map(Wishlist::getProductId).toList();
        return productCatalogService.getSummariesByProductIds(productIds, true);
    }

    @Transactional
    public void add(Long memberId, Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
        if (wishlistRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        Wishlist wishlist = new Wishlist();
        wishlist.setMemberId(memberId);
        wishlist.setProductId(productId);
        wishlist.setCreatedAt(clock.instant());
        wishlistRepository.save(wishlist);
    }

    @Transactional
    public void remove(Long memberId, Long productId) {
        Wishlist wishlist = wishlistRepository.findByMemberIdAndProductId(memberId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "위시리스트 항목을 찾을 수 없습니다."));
        wishlistRepository.delete(wishlist);
    }
}
