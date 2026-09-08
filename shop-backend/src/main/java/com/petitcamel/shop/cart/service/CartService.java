package com.petitcamel.shop.cart.service;

import com.petitcamel.shop.cart.domain.Cart;
import com.petitcamel.shop.cart.domain.CartItem;
import com.petitcamel.shop.cart.dto.CartItemQuantityRequest;
import com.petitcamel.shop.cart.dto.CartItemRequest;
import com.petitcamel.shop.cart.dto.CartItemResponse;
import com.petitcamel.shop.cart.dto.CartMergeRequest;
import com.petitcamel.shop.cart.dto.CartResponse;
import com.petitcamel.shop.cart.repository.CartItemRepository;
import com.petitcamel.shop.cart.repository.CartRepository;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    public static final BigDecimal DELIVERY_FEE = BigDecimal.valueOf(3000);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductSkuRepository productSkuRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            Clock clock) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productSkuRepository = productSkuRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long memberId) {
        Cart cart = cartRepository.findByMemberId(memberId).orElse(null);
        if (cart == null) {
            return emptyCart();
        }
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(Long memberId, CartItemRequest request) {
        Cart cart = getOrCreateCart(memberId);
        ProductSku sku = requireOnSaleSku(request.skuId());
        int available = availableQuantity(sku.getSkuId());
        int desired = request.quantity();

        CartItem existing = cartItemRepository.findByCartIdAndSkuId(cart.getCartId(), sku.getSkuId())
                .orElse(null);
        if (existing != null) {
            desired = existing.getQuantity() + request.quantity();
        }

        assertEnoughStock(available, desired);

        Instant now = clock.instant();
        if (existing == null) {
            CartItem item = new CartItem();
            item.setCartId(cart.getCartId());
            item.setSkuId(sku.getSkuId());
            item.setQuantity(request.quantity());
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            cartItemRepository.save(item);
        } else {
            existing.setQuantity(desired);
            existing.setUpdatedAt(now);
            cartItemRepository.save(existing);
        }

        touchCart(cart, now);
        log.info("Cart item added memberId={} skuId={} quantity={}", memberId, sku.getSkuId(), desired);
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long memberId, Long cartItemId, CartItemQuantityRequest request) {
        Cart cart = requireCart(memberId);
        CartItem item = requireCartItem(cart.getCartId(), cartItemId);
        int available = availableQuantity(item.getSkuId());
        assertEnoughStock(available, request.quantity());

        Instant now = clock.instant();
        item.setQuantity(request.quantity());
        item.setUpdatedAt(now);
        cartItemRepository.save(item);
        touchCart(cart, now);
        return buildCartResponse(cart);
    }

    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        Cart cart = requireCart(memberId);
        CartItem item = requireCartItem(cart.getCartId(), cartItemId);
        cartItemRepository.delete(item);
        touchCart(cart, clock.instant());
    }

    @Transactional
    public CartResponse merge(Long memberId, CartMergeRequest request) {
        Cart cart = getOrCreateCart(memberId);
        Instant now = clock.instant();

        for (CartItemRequest guestItem : request.items()) {
            if (guestItem == null || guestItem.skuId() == null || guestItem.quantity() == null) {
                continue;
            }
            if (guestItem.quantity() <= 0) {
                continue;
            }

            ProductSku sku;
            try {
                sku = requireOnSaleSku(guestItem.skuId());
            } catch (BusinessException ex) {
                continue;
            }

            int available = availableQuantity(sku.getSkuId());
            if (available <= 0) {
                continue;
            }

            CartItem existing = cartItemRepository.findByCartIdAndSkuId(cart.getCartId(), sku.getSkuId())
                    .orElse(null);
            int merged = guestItem.quantity() + (existing == null ? 0 : existing.getQuantity());
            int clamped = Math.min(merged, available);

            if (existing == null) {
                CartItem item = new CartItem();
                item.setCartId(cart.getCartId());
                item.setSkuId(sku.getSkuId());
                item.setQuantity(clamped);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                cartItemRepository.save(item);
            } else {
                existing.setQuantity(clamped);
                existing.setUpdatedAt(now);
                cartItemRepository.save(existing);
            }
        }

        touchCart(cart, now);
        return buildCartResponse(cart);
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getCartId());
        if (items.isEmpty()) {
            return emptyCart();
        }

        List<Long> skuIds = items.stream().map(CartItem::getSkuId).toList();
        Map<Long, ProductSku> skus = productSkuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getSkuId, Function.identity()));
        List<Long> productIds = skus.values().stream().map(ProductSku::getProductId).distinct().toList();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        Map<Long, Integer> availableBySku = loadAvailableBySku(skuIds);

        List<CartItemResponse> responses = new ArrayList<>();
        BigDecimal productAmount = BigDecimal.ZERO;

        for (CartItem item : items) {
            ProductSku sku = skus.get(item.getSkuId());
            if (sku == null) {
                continue;
            }
            Product product = products.get(sku.getProductId());
            if (product == null) {
                continue;
            }
            BigDecimal unitPrice = product.getSalePrice().add(
                    sku.getAdditionalPrice() == null ? BigDecimal.ZERO : sku.getAdditionalPrice());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            productAmount = productAmount.add(lineTotal);
            responses.add(new CartItemResponse(
                    item.getCartItemId(),
                    sku.getSkuId(),
                    product.getProductId(),
                    product.getProductName(),
                    sku.getColor() + "/" + sku.getSize(),
                    unitPrice,
                    item.getQuantity(),
                    availableBySku.getOrDefault(sku.getSkuId(), 0),
                    lineTotal));
        }

        BigDecimal deliveryAmount = productAmount.compareTo(BigDecimal.ZERO) > 0
                ? DELIVERY_FEE
                : BigDecimal.ZERO;
        return new CartResponse(responses, productAmount, deliveryAmount, productAmount.add(deliveryAmount));
    }

    private Map<Long, Integer> loadAvailableBySku(List<Long> skuIds) {
        Map<Long, Integer> available = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findBySkuIdIn(skuIds)) {
            available.put(inventory.getSkuId(), inventory.getAvailableQuantity());
        }
        return available;
    }

    private int availableQuantity(Long skuId) {
        return inventoryRepository.findBySkuId(skuId)
                .map(Inventory::getAvailableQuantity)
                .orElse(0);
    }

    private void assertEnoughStock(int available, int quantity) {
        if (available < quantity) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "재고가 부족합니다. (가용 수량: " + available + ")");
        }
    }

    private ProductSku requireOnSaleSku(Long skuId) {
        ProductSku sku = productSkuRepository.findById(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "SKU를 찾을 수 없습니다."));
        if (sku.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "판매 중인 SKU만 장바구니에 담을 수 있습니다.");
        }
        return sku;
    }

    private Cart getOrCreateCart(Long memberId) {
        return cartRepository.findByMemberId(memberId).orElseGet(() -> {
            Instant now = clock.instant();
            Cart cart = new Cart();
            cart.setMemberId(memberId);
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            return cartRepository.save(cart);
        });
    }

    private Cart requireCart(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "장바구니를 찾을 수 없습니다."));
    }

    private CartItem requireCartItem(Long cartId, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다."));
        if (!item.getCartId().equals(cartId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다.");
        }
        return item;
    }

    private void touchCart(Cart cart, Instant now) {
        cart.setUpdatedAt(now);
        cartRepository.save(cart);
    }

    private CartResponse emptyCart() {
        return new CartResponse(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
