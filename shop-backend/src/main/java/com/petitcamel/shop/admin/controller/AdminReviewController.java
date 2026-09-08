package com.petitcamel.shop.admin.controller;

import com.petitcamel.shop.admin.dto.ReviewStatusUpdateRequest;
import com.petitcamel.shop.review.dto.ReviewResponse;
import com.petitcamel.shop.review.service.ReviewService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PatchMapping("/{reviewId}/status")
    public ReviewResponse updateStatus(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewStatusUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        Long actorId = principal == null ? null : principal.getMemberId();
        return reviewService.updateAdminStatus(reviewId, request.status(), actorId);
    }
}
