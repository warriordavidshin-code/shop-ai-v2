package com.petitcamel.shop.review.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.review.dto.CreateReviewRequest;
import com.petitcamel.shop.review.dto.ReviewResponse;
import com.petitcamel.shop.review.dto.UpdateReviewRequest;
import com.petitcamel.shop.review.service.ReviewService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/products/{productId}/reviews")
    public PageResponse<ReviewResponse> list(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewService.listVisible(productId, page, size);
    }

    @PostMapping("/api/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.create(principal.getMemberId(), productId, request);
    }

    @PatchMapping("/api/reviews/{reviewId}")
    public ReviewResponse update(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateReviewRequest request) {
        return reviewService.update(principal.getMemberId(), reviewId, request);
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long reviewId) {
        reviewService.delete(principal.getMemberId(), reviewId);
        return ResponseEntity.noContent().build();
    }
}
