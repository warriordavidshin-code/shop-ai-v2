package com.petitcamel.shop.pricing.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.pricing.domain.PricingPolicy;
import com.petitcamel.shop.pricing.dto.PricingDtos;
import com.petitcamel.shop.pricing.repository.PricingPolicyRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PricingPolicyService {

    private final PricingPolicyRepository repository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public PricingPolicyService(PricingPolicyRepository repository, AuditLogService auditLogService, Clock clock) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PricingPolicy current() {
        return repository.findById(PricingPolicy.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "가격 정책 설정이 없습니다."));
    }

    @Transactional(readOnly = true)
    public PricingDtos.PolicyResponse get() {
        return toResponse(current());
    }

    @Transactional
    public PricingDtos.PolicyResponse update(PricingDtos.PolicyUpdateRequest request, Long actorId) {
        if (request.minimumMarginRate().compareTo(request.premiumMarginRate()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "최소 마진율은 고마진 목표보다 클 수 없습니다.");
        }
        PricingPolicy policy = current();
        if (request.version() != null && !request.version().equals(policy.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 관리자가 설정을 변경했습니다. 새로고침 후 다시 저장해 주세요.");
        }
        policy.setCompetitiveMarginRate(request.competitiveMarginRate());
        policy.setStandardMarginRate(request.standardMarginRate());
        policy.setPremiumMarginRate(request.premiumMarginRate());
        policy.setMinimumMarginRate(request.minimumMarginRate());
        policy.setRoundingMode(request.roundingMode());
        policy.setMarginBasis(request.marginBasis());
        policy.setTaxType(request.taxType());
        policy.setVatRate(request.vatRate());
        policy.setCostingMethod(request.costingMethod());
        policy.setPaymentFeeRate(request.paymentFeeRate());
        policy.setShippingCostPerOrder(request.shippingCostPerOrder());
        policy.setOtherSellingCostRate(request.otherSellingCostRate());
        policy.setLowStockCoverDays(request.lowStockCoverDays());
        policy.setSafetyStockDays(request.safetyStockDays());
        policy.setDefaultLeadDays(request.defaultLeadDays());
        policy.setSalesWindowDays(request.salesWindowDays());
        policy.setSlowMovingDays(request.slowMovingDays());
        policy.setSlowMovingMaxSales(request.slowMovingMaxSales());
        policy.setUpdatedBy(actorId);
        policy.setUpdatedAt(clock.instant());
        try {
            repository.saveAndFlush(policy);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 관리자가 설정을 변경했습니다. 새로고침 후 다시 저장해 주세요.");
        }
        auditLogService.record(actorId, "PRICING_POLICY_UPDATE", "PRICING_POLICY", "1",
                "standard=" + request.standardMarginRate() + ", minimum=" + request.minimumMarginRate()
                        + ", rounding=" + request.roundingMode() + ", costing=" + request.costingMethod());
        return toResponse(policy);
    }

    public static PriceCalculator.Basis basisOf(PricingPolicy policy) {
        return PriceCalculator.Basis.of(policy.getMarginBasis(), policy.getTaxType(), policy.getVatRate());
    }

    private static PricingDtos.PolicyResponse toResponse(PricingPolicy p) {
        return new PricingDtos.PolicyResponse(
                p.getCompetitiveMarginRate(), p.getStandardMarginRate(), p.getPremiumMarginRate(),
                p.getMinimumMarginRate(), p.getRoundingMode(), p.getMarginBasis(), p.getTaxType(), p.getVatRate(),
                p.getCostingMethod(), p.getPaymentFeeRate(), p.getShippingCostPerOrder(),
                p.getOtherSellingCostRate(), p.getLowStockCoverDays(), p.getSafetyStockDays(),
                p.getDefaultLeadDays(), p.getSalesWindowDays(), p.getSlowMovingDays(), p.getSlowMovingMaxSales(),
                p.getUpdatedAt(), p.getVersion());
    }
}
