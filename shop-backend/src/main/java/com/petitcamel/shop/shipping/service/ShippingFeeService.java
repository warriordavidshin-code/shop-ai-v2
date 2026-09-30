package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.shipping.domain.ExtraAreaSource;
import com.petitcamel.shop.shipping.domain.ShippingExtraArea;
import com.petitcamel.shop.shipping.domain.ShippingPolicy;
import com.petitcamel.shop.shipping.dto.ExtraAreaRequest;
import com.petitcamel.shop.shipping.dto.ExtraAreaResponse;
import com.petitcamel.shop.shipping.dto.ShippingPolicyResponse;
import com.petitcamel.shop.shipping.dto.ShippingPolicyUpdateRequest;
import com.petitcamel.shop.shipping.dto.ShippingQuote;
import com.petitcamel.shop.shipping.repository.ShippingExtraAreaRepository;
import com.petitcamel.shop.shipping.repository.ShippingPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Shipping fees from the enabled {@code shipping_policy} row and the {@code shipping_extra_area} ranges. */
@Service
public class ShippingFeeService {

    static final String DEFAULT_POLICY_NAME = "기본 배송정책";

    private final ShippingPolicyRepository policyRepository;
    private final ShippingExtraAreaRepository extraAreaRepository;
    private final AuditLogService auditLogService;

    public ShippingFeeService(
            ShippingPolicyRepository policyRepository,
            ShippingExtraAreaRepository extraAreaRepository,
            AuditLogService auditLogService) {
        this.policyRepository = policyRepository;
        this.extraAreaRepository = extraAreaRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public ShippingPolicyResponse getPolicy() {
        return toResponse(activePolicy());
    }

    /** Fee for a product amount; {@code postcode} may be null (cart page) in which case no surcharge applies. */
    @Transactional(readOnly = true)
    public ShippingQuote quote(BigDecimal productAmount, String postcode) {
        ShippingPolicy policy = activePolicy();
        ShippingFeeCalculator.Policy rule = new ShippingFeeCalculator.Policy(
                policy.getBaseShippingFee(), policy.getFreeShippingThreshold(),
                policy.getJejuExtraFee(), policy.getRemoteAreaExtraFee());
        Optional<ShippingExtraArea> area = matchArea(postcode);
        return ShippingFeeCalculator.quote(rule, productAmount,
                area.map(ShippingExtraArea::getAreaType).orElse(null),
                area.map(ShippingExtraArea::getExtraFee).orElse(null));
    }

    @Transactional(readOnly = true)
    public long returnShippingFee() {
        return activePolicy().getReturnShippingFee();
    }

    @Transactional
    public ShippingPolicyResponse updatePolicy(ShippingPolicyUpdateRequest request, Long actorMemberId) {
        ShippingPolicy policy = policyRepository.findFirstByEnabledTrueOrderByShippingPolicyIdAsc()
                .orElseGet(ShippingFeeService::defaultPolicy);
        String before = describe(policy);
        policy.setBaseShippingFee(request.baseShippingFee());
        policy.setFreeShippingThreshold(request.freeShippingAmount());
        policy.setJejuExtraFee(request.jejuExtraFee());
        policy.setRemoteAreaExtraFee(request.remoteAreaExtraFee());
        policy.setReturnShippingFee(request.returnShippingFee());
        policy.setExchangeShippingFee(request.exchangeShippingFee());
        ShippingPolicy saved = policyRepository.saveAndFlush(policy);
        auditLogService.record(actorMemberId, "SHIPPING_POLICY_UPDATE", "SHIPPING_POLICY",
                String.valueOf(saved.getShippingPolicyId()), "before={" + before + "}, after={" + describe(saved) + "}");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ExtraAreaResponse> listExtraAreas() {
        ShippingPolicy policy = activePolicy();
        return extraAreaRepository.findAllByOrderByAreaTypeAscPostalCodeFromAsc().stream()
                .map(area -> toResponse(area, policy))
                .toList();
    }

    @Transactional
    public ExtraAreaResponse addExtraArea(ExtraAreaRequest request, Long actorMemberId) {
        if (request.postalCodeFrom().compareTo(request.postalCodeTo()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "시작 우편번호가 끝 우편번호보다 클 수 없습니다.");
        }
        ShippingExtraArea area = new ShippingExtraArea();
        area.setAreaType(request.areaType());
        area.setAreaName(request.areaName().trim());
        area.setPostalCodeFrom(request.postalCodeFrom());
        area.setPostalCodeTo(request.postalCodeTo());
        area.setExtraFee(request.extraFee());
        area.setEnabled(true);
        area.setSource(ExtraAreaSource.MANUAL);
        ShippingExtraArea saved = extraAreaRepository.saveAndFlush(area);
        auditLogService.record(actorMemberId, "SHIPPING_AREA_ADD", "SHIPPING_AREA",
                String.valueOf(saved.getShippingExtraAreaId()), describe(saved));
        return toResponse(saved, activePolicy());
    }

    @Transactional
    public ExtraAreaResponse setExtraAreaEnabled(Long areaId, boolean enabled, Long actorMemberId) {
        ShippingExtraArea area = requireArea(areaId);
        area.setEnabled(enabled);
        ShippingExtraArea saved = extraAreaRepository.saveAndFlush(area);
        auditLogService.record(actorMemberId, "SHIPPING_AREA_UPDATE", "SHIPPING_AREA", String.valueOf(areaId),
                describe(saved) + ", enabled=" + enabled);
        return toResponse(saved, activePolicy());
    }

    @Transactional
    public void deleteExtraArea(Long areaId, Long actorMemberId) {
        ShippingExtraArea area = requireArea(areaId);
        extraAreaRepository.delete(area);
        auditLogService.record(actorMemberId, "SHIPPING_AREA_DELETE", "SHIPPING_AREA", String.valueOf(areaId),
                describe(area));
    }

    static String normalizePostcode(String postcode) {
        if (postcode == null) {
            return null;
        }
        String digits = postcode.replaceAll("\\D", "");
        return digits.length() == 5 ? digits : null;
    }

    /** JEJU wins over REMOTE when ranges overlap. */
    private Optional<ShippingExtraArea> matchArea(String postcode) {
        String normalized = normalizePostcode(postcode);
        if (normalized == null) {
            return Optional.empty();
        }
        return extraAreaRepository.findEnabledContaining(normalized).stream()
                .min(Comparator.comparingInt(a -> a.getAreaType().ordinal()));
    }

    private ShippingPolicy activePolicy() {
        return policyRepository.findFirstByEnabledTrueOrderByShippingPolicyIdAsc()
                .orElseGet(ShippingFeeService::defaultPolicy);
    }

    private ShippingExtraArea requireArea(Long areaId) {
        return extraAreaRepository.findById(areaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "추가배송비 지역을 찾을 수 없습니다."));
    }

    private static ShippingPolicy defaultPolicy() {
        ShippingPolicy policy = new ShippingPolicy();
        policy.setName(DEFAULT_POLICY_NAME);
        policy.setBaseShippingFee(3000);
        policy.setFreeShippingThreshold(50000);
        policy.setJejuExtraFee(3000);
        policy.setRemoteAreaExtraFee(5000);
        policy.setReturnShippingFee(3000);
        policy.setExchangeShippingFee(6000);
        policy.setEnabled(true);
        return policy;
    }

    private static String describe(ShippingPolicy p) {
        return "base=" + p.getBaseShippingFee() + ", free=" + p.getFreeShippingThreshold()
                + ", jeju=" + p.getJejuExtraFee() + ", remote=" + p.getRemoteAreaExtraFee()
                + ", return=" + p.getReturnShippingFee() + ", exchange=" + p.getExchangeShippingFee();
    }

    private static String describe(ShippingExtraArea a) {
        return a.getAreaType() + " " + a.getAreaName() + " " + a.getPostalCodeFrom() + "-" + a.getPostalCodeTo()
                + (a.getExtraFee() == null ? "" : ", fee=" + a.getExtraFee());
    }

    private static ShippingPolicyResponse toResponse(ShippingPolicy p) {
        return new ShippingPolicyResponse(
                p.getShippingPolicyId(),
                p.getName(),
                p.getBaseShippingFee(),
                p.getFreeShippingThreshold(),
                p.getJejuExtraFee(),
                p.getRemoteAreaExtraFee(),
                p.getReturnShippingFee(),
                p.getExchangeShippingFee(),
                p.getUpdatedAt());
    }

    private static ExtraAreaResponse toResponse(ShippingExtraArea area, ShippingPolicy policy) {
        return new ExtraAreaResponse(
                area.getShippingExtraAreaId(),
                area.getAreaType(),
                area.getAreaName(),
                area.getPostalCodeFrom(),
                area.getPostalCodeTo(),
                area.getExtraFee(),
                area.effectiveFee(policy),
                area.isEnabled(),
                area.getSource());
    }
}
