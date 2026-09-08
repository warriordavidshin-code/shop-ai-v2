package com.petitcamel.shop.recommendation.controller;

import com.petitcamel.shop.recommendation.dto.OutfitRecommendationRequest;
import com.petitcamel.shop.recommendation.dto.OutfitRecommendationResponse;
import com.petitcamel.shop.recommendation.dto.RecommendationEventRequest;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationRequest;
import com.petitcamel.shop.recommendation.dto.SizeRecommendationResponse;
import com.petitcamel.shop.recommendation.service.OutfitRecommendationService;
import com.petitcamel.shop.recommendation.service.SizeRecommendationService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final SizeRecommendationService sizeRecommendationService;
    private final OutfitRecommendationService outfitRecommendationService;

    public AiController(
            SizeRecommendationService sizeRecommendationService,
            OutfitRecommendationService outfitRecommendationService) {
        this.sizeRecommendationService = sizeRecommendationService;
        this.outfitRecommendationService = outfitRecommendationService;
    }

    @PostMapping("/size-recommendations")
    public SizeRecommendationResponse sizeRecommend(@Valid @RequestBody SizeRecommendationRequest request) {
        return sizeRecommendationService.recommend(request);
    }

    @PostMapping("/outfit-recommendations")
    public OutfitRecommendationResponse outfitRecommend(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody OutfitRecommendationRequest request) {
        Long memberId = principal == null ? null : principal.getMemberId();
        return outfitRecommendationService.recommend(memberId, request);
    }

    @PostMapping("/recommendations/{recommendationId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public void recordEvent(
            @PathVariable Long recommendationId,
            @Valid @RequestBody RecommendationEventRequest request) {
        outfitRecommendationService.recordEvent(recommendationId, request);
    }
}
