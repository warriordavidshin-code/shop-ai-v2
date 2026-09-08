package com.petitcamel.shop.wishlist.controller;

import com.petitcamel.shop.product.dto.ProductSummaryResponse;
import com.petitcamel.shop.security.MemberPrincipal;
import com.petitcamel.shop.wishlist.service.WishlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public List<ProductSummaryResponse> list(@AuthenticationPrincipal MemberPrincipal principal) {
        return wishlistService.list(principal.getMemberId());
    }

    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void add(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long productId) {
        wishlistService.add(principal.getMemberId(), productId);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long productId) {
        wishlistService.remove(principal.getMemberId(), productId);
        return ResponseEntity.noContent().build();
    }
}
