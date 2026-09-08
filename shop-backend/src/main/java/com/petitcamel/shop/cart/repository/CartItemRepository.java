package com.petitcamel.shop.cart.repository;

import com.petitcamel.shop.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndSkuId(Long cartId, Long skuId);

    void deleteById(Long cartItemId);

    void deleteByCartIdAndSkuIdIn(Long cartId, Collection<Long> skuIds);
}
