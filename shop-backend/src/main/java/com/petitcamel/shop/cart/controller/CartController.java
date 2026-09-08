package com.petitcamel.shop.cart.controller;

import com.petitcamel.shop.cart.dto.CartItemQuantityRequest;
import com.petitcamel.shop.cart.dto.CartItemRequest;
import com.petitcamel.shop.cart.dto.CartMergeRequest;
import com.petitcamel.shop.cart.dto.CartResponse;
import com.petitcamel.shop.cart.service.CartService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal MemberPrincipal principal) {
        return cartService.getCart(principal.getMemberId());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(principal.getMemberId(), request);
    }

    @PatchMapping("/items/{cartItemId}")
    public CartResponse updateItem(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemQuantityRequest request) {
        return cartService.updateItem(principal.getMemberId(), cartItemId, request);
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long cartItemId) {
        cartService.removeItem(principal.getMemberId(), cartItemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merge")
    public CartResponse merge(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody CartMergeRequest request) {
        return cartService.merge(principal.getMemberId(), request);
    }
}
