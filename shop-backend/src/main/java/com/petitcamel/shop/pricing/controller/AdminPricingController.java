package com.petitcamel.shop.pricing.controller;

import com.petitcamel.shop.cost.dto.CostDtos;
import com.petitcamel.shop.cost.service.ProductCostService;
import com.petitcamel.shop.pricing.dto.PricingDtos;
import com.petitcamel.shop.pricing.service.PricingPolicyService;
import com.petitcamel.shop.pricing.service.PricingService;
import com.petitcamel.shop.pricing.service.ProductPriceHistoryService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminPricingController {

    private final PricingPolicyService policyService;
    private final PricingService pricingService;
    private final ProductCostService costService;
    private final ProductPriceHistoryService priceHistoryService;

    public AdminPricingController(
            PricingPolicyService policyService,
            PricingService pricingService,
            ProductCostService costService,
            ProductPriceHistoryService priceHistoryService) {
        this.policyService = policyService;
        this.pricingService = pricingService;
        this.costService = costService;
        this.priceHistoryService = priceHistoryService;
    }

    @GetMapping("/api/admin/pricing/policies")
    public PricingDtos.PolicyResponse getPolicy() {
        return policyService.get();
    }

    @PutMapping("/api/admin/pricing/policies")
    public PricingDtos.PolicyResponse updatePolicy(
            @Valid @RequestBody PricingDtos.PolicyUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return policyService.update(request, actorId(principal));
    }

    @PostMapping("/api/admin/pricing/recommend")
    public PricingDtos.RecommendResponse recommend(@Valid @RequestBody PricingDtos.RecommendRequest request) {
        return pricingService.recommend(request);
    }

    @GetMapping("/api/admin/products/{productId}/cost-history")
    public List<CostDtos.CostHistoryResponse> costHistory(@PathVariable Long productId) {
        return costService.history(productId);
    }

    @GetMapping("/api/admin/products/{productId}/cost-summary")
    public CostDtos.CostSummaryResponse costSummary(@PathVariable Long productId) {
        return costService.summary(productId);
    }

    @GetMapping("/api/admin/products/{productId}/price-history")
    public List<PricingDtos.PriceHistoryResponse> priceHistory(@PathVariable Long productId) {
        return priceHistoryService.list(productId).stream()
                .map(h -> new PricingDtos.PriceHistoryResponse(h.getId(), h.getNormalPriceBefore(),
                        h.getNormalPriceAfter(), h.getSalePriceBefore(), h.getSalePriceAfter(), h.getReason(),
                        h.getChangedBy(), h.getChangedAt()))
                .toList();
    }

    private static Long actorId(MemberPrincipal principal) {
        return principal == null ? null : principal.getMemberId();
    }
}
