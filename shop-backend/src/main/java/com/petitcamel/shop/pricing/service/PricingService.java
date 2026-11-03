package com.petitcamel.shop.pricing.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.cost.dto.CostDtos;
import com.petitcamel.shop.cost.service.ProductCostService;
import com.petitcamel.shop.pricing.domain.PricingPolicy;
import com.petitcamel.shop.pricing.dto.PricingDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Recommended sale prices from cost and the target gross margins in the pricing policy. Pure arithmetic;
 * the "competitive" tier is an internal-margin price, not a market comparison.
 */
@Service
public class PricingService {

    private final PricingPolicyService policyService;
    private final ProductCostService costService;

    public PricingService(PricingPolicyService policyService, ProductCostService costService) {
        this.policyService = policyService;
        this.costService = costService;
    }

    @Transactional(readOnly = true)
    public PricingDtos.RecommendResponse recommend(PricingDtos.RecommendRequest request) {
        PricingPolicy policy = policyService.current();
        BigDecimal cost = request.unitCost();
        String costSource = "INPUT";
        if (cost == null && request.productId() != null) {
            CostDtos.CostSummaryResponse summary = costService.summary(request.productId());
            cost = summary.appliedUnitCost();
            costSource = summary.appliedCostSource();
        }
        if (cost == null || cost.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "매입원가를 입력하거나 입고 확정된 원가가 있는 상품을 선택해 주세요.");
        }
        return recommend(cost, costSource, request.currentSalePrice(), policy);
    }

    public PricingDtos.RecommendResponse recommend(BigDecimal cost, String costSource, BigDecimal currentSalePrice,
                                                   PricingPolicy policy) {
        PriceCalculator.Basis basis = PricingPolicyService.basisOf(policy);
        BigDecimal minimum = policy.getMinimumMarginRate();
        List<String> warnings = new ArrayList<>();
        List<PricingDtos.PriceOption> options = new ArrayList<>();
        options.add(option("COMPETITIVE", "경쟁력 가격", policy.getCompetitiveMarginRate(), cost, basis, policy,
                "내부 목표 마진율 기준 가격입니다. 외부 시장조사·경쟁사 가격이 아닙니다."));
        options.add(option("STANDARD", "표준 가격", policy.getStandardMarginRate(), cost, basis, policy, null));
        options.add(option("PREMIUM", "고마진 가격", policy.getPremiumMarginRate(), cost, basis, policy, null));
        for (PricingDtos.PriceOption option : options) {
            if (option.belowMinimum()) {
                warnings.add(option.label() + "의 조정 후 마진율이 최소 마진율("
                        + percent(minimum) + ")보다 낮습니다.");
            }
        }

        PricingDtos.CurrentPriceCheck current = null;
        if (currentSalePrice != null && currentSalePrice.signum() > 0) {
            BigDecimal rate = PriceCalculator.marginRate(currentSalePrice, cost, basis);
            boolean below = rate != null && rate.compareTo(minimum) < 0;
            current = new PricingDtos.CurrentPriceCheck(currentSalePrice, rate,
                    PriceCalculator.grossProfit(currentSalePrice, cost, basis).longValue(), below);
            if (below) {
                warnings.add("현재 판매가 " + won(currentSalePrice) + "원의 마진율(" + percent(rate)
                        + ")이 최소 마진율(" + percent(minimum) + ")보다 낮습니다.");
            }
            if (rate != null && rate.signum() < 0) {
                warnings.add("현재 판매가가 원가보다 낮습니다.");
            }
        }
        if ("SIMPLIFIED".equals(policy.getTaxType()) || "EXEMPT".equals(policy.getTaxType())) {
            warnings.add("간이·면세 과세유형의 부가세 처리는 가격 산식과 별도로 관리됩니다. 신고 세액은 세무 기준으로 확인해 주세요.");
        }

        String explanation = explanation(cost, options, policy, basis);
        return new PricingDtos.RecommendResponse(cost.setScale(0, RoundingMode.HALF_UP), costSource,
                basis.excludeVat() ? "VAT_EXCLUDED" : "VAT_INCLUDED", policy.getRoundingMode(), minimum, options,
                current, warnings, explanation);
    }

    private static PricingDtos.PriceOption option(String tier, String label, BigDecimal targetRate, BigDecimal cost,
                                                  PriceCalculator.Basis basis, PricingPolicy policy, String note) {
        BigDecimal raw = PriceCalculator.targetPrice(cost, targetRate, basis).setScale(0, RoundingMode.CEILING);
        long rounded = PriceCalculator.round(raw, policy.getRoundingMode());
        BigDecimal roundedValue = BigDecimal.valueOf(rounded);
        BigDecimal rate = PriceCalculator.marginRate(roundedValue, cost, basis);
        boolean below = rate != null && rate.compareTo(policy.getMinimumMarginRate()) < 0;
        return new PricingDtos.PriceOption(tier, label, targetRate, raw, rounded, rate,
                PriceCalculator.grossProfit(roundedValue, cost, basis).longValue(), below, note);
    }

    private static String explanation(BigDecimal cost, List<PricingDtos.PriceOption> options, PricingPolicy policy,
                                      PriceCalculator.Basis basis) {
        StringBuilder text = new StringBuilder();
        text.append("상품원가 ").append(won(cost.setScale(0, RoundingMode.HALF_UP))).append("원 기준으로 ")
                .append("목표 판매가 = 원가 ÷ (1 - 목표 매출총이익률) 식을 적용했습니다. ");
        if (basis.excludeVat()) {
            text.append("일반과세 기준으로 판매가에서 부가세(").append(percent(policy.getVatRate()))
                    .append(")를 제외한 금액으로 마진을 계산했으며, 원가는 부가세 제외 공급가로 입력해야 합니다. ");
        } else {
            text.append("마진율은 부가세 포함 판매가 기준의 단순 매출총이익률입니다. ");
        }
        text.append("끝자리 규칙(").append(roundingLabel(policy.getRoundingMode()))
                .append(")을 적용한 뒤 마진율을 다시 계산했습니다. ");
        for (PricingDtos.PriceOption option : options) {
            text.append(option.label()).append(' ').append(won(option.roundedPrice())).append("원(")
                    .append(percent(option.marginRate())).append(") ");
        }
        return text.toString().trim();
    }

    public static String roundingLabel(String mode) {
        return switch (mode == null ? "NONE" : mode) {
            case "UP_100" -> "100원 단위 올림";
            case "UP_1000" -> "1,000원 단위 올림";
            case "END_900" -> "끝자리 900원";
            case "END_900_ROUND_9000" -> "끝자리 900원, x8,900원은 x9,000원";
            default -> "조정 없음";
        };
    }

    private static String won(Object amount) {
        BigDecimal value = amount instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(amount));
        return String.format("%,d", value.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static String percent(BigDecimal rate) {
        if (rate == null) {
            return "-";
        }
        return rate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
