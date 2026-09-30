package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.shipping.domain.ExtraAreaType;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Shipping fees from the admin-managed {@code shipping_policy} / {@code shipping_extra_area} tables. */
@Service
public class ShippingFeeService {

    private final ShippingPolicyRepository policyRepository;
    private final ShippingExtraAreaRepository extraAreaRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ShippingFeeService(
            ShippingPolicyRepository policyRepository,
            ShippingExtraAreaRepository extraAreaRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.policyRepository = policyRepository;
        this.extraAreaRepository = extraAreaRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ShippingPolicyResponse getPolicy() {
        return toResponse(loadPolicy());
    }

    /** Fee for a product amount; {@code postcode} may be null (cart page) in which case no surcharge applies. */
    @Transactional(readOnly = true)
    public ShippingQuote quote(BigDecimal productAmount, String postcode) {
        ShippingPolicy policy = loadPolicy();
        return ShippingFeeCalculator.quote(toCalculatorPolicy(policy), productAmount, classify(postcode));
    }

    @Transactional(readOnly = true)
    public BigDecimal returnShippingFee() {
        return loadPolicy().getReturnShippingFee();
    }

    @Transactional(readOnly = true)
    public ExtraAreaType classify(String postcode) {
        String normalized = normalizePostcode(postcode);
        if (normalized == null) {
            return null;
        }
        return extraAreaRepository.findAll().stream()
                .filter(area -> area.contains(normalized))
                .map(ShippingExtraArea::getAreaType)
                .min(Comparator.comparingInt(ExtraAreaType::ordinal))
                .orElse(null);
    }

    @Transactional
    public ShippingPolicyResponse updatePolicy(ShippingPolicyUpdateRequest request, Long actorMemberId) {
        ShippingPolicy policy = policyRepository.findById(ShippingPolicy.SINGLETON_ID).orElseGet(this::defaultPolicy);
        String before = describe(policy);
        policy.setBaseShippingFee(request.baseShippingFee());
        policy.setFreeShippingAmount(request.freeShippingAmount());
        policy.setJejuExtraFee(request.jejuExtraFee());
        policy.setRemoteAreaExtraFee(request.remoteAreaExtraFee());
        policy.setReturnShippingFee(request.returnShippingFee());
        policy.setUpdatedBy(actorMemberId);
        policy.setUpdatedAt(clock.instant());
        ShippingPolicy saved = policyRepository.save(policy);
        auditLogService.record(actorMemberId, "SHIPPING_POLICY_UPDATE", "SHIPPING_POLICY", "1",
                "before={" + before + "}, after={" + describe(saved) + "}");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ExtraAreaResponse> listExtraAreas() {
        return extraAreaRepository.findAllByOrderByAreaTypeAscPostcodeFromAsc().stream()
                .map(ShippingFeeService::toResponse)
                .toList();
    }

    @Transactional
    public ExtraAreaResponse addExtraArea(ExtraAreaRequest request, Long actorMemberId) {
        if (request.postcodeFrom().compareTo(request.postcodeTo()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "시작 우편번호가 끝 우편번호보다 클 수 없습니다.");
        }
        ShippingExtraArea area = new ShippingExtraArea();
        area.setAreaType(request.areaType());
        area.setPostcodeFrom(request.postcodeFrom());
        area.setPostcodeTo(request.postcodeTo());
        area.setNote(request.note() == null ? null : request.note().trim());
        area.setCreatedAt(clock.instant());
        ShippingExtraArea saved = extraAreaRepository.save(area);
        auditLogService.record(actorMemberId, "SHIPPING_AREA_ADD", "SHIPPING_AREA", String.valueOf(saved.getAreaId()),
                saved.getAreaType() + " " + saved.getPostcodeFrom() + "-" + saved.getPostcodeTo());
        return toResponse(saved);
    }

    @Transactional
    public void deleteExtraArea(Long areaId, Long actorMemberId) {
        ShippingExtraArea area = extraAreaRepository.findById(areaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "추가배송비 지역을 찾을 수 없습니다."));
        extraAreaRepository.delete(area);
        auditLogService.record(actorMemberId, "SHIPPING_AREA_DELETE", "SHIPPING_AREA", String.valueOf(areaId),
                area.getAreaType() + " " + area.getPostcodeFrom() + "-" + area.getPostcodeTo());
    }

    static String normalizePostcode(String postcode) {
        if (postcode == null) {
            return null;
        }
        String digits = postcode.replaceAll("\\D", "");
        return digits.length() == 5 ? digits : null;
    }

    private ShippingPolicy loadPolicy() {
        return policyRepository.findById(ShippingPolicy.SINGLETON_ID).orElseGet(this::defaultPolicy);
    }

    private ShippingPolicy defaultPolicy() {
        ShippingPolicy policy = new ShippingPolicy();
        policy.setPolicyId(ShippingPolicy.SINGLETON_ID);
        policy.setBaseShippingFee(BigDecimal.valueOf(3000));
        policy.setFreeShippingAmount(BigDecimal.valueOf(50000));
        policy.setJejuExtraFee(BigDecimal.valueOf(3000));
        policy.setRemoteAreaExtraFee(BigDecimal.valueOf(5000));
        policy.setReturnShippingFee(BigDecimal.valueOf(3000));
        policy.setUpdatedAt(Instant.EPOCH);
        return policy;
    }

    private static ShippingFeeCalculator.Policy toCalculatorPolicy(ShippingPolicy policy) {
        return new ShippingFeeCalculator.Policy(
                policy.getBaseShippingFee(),
                policy.getFreeShippingAmount(),
                policy.getJejuExtraFee(),
                policy.getRemoteAreaExtraFee());
    }

    private static String describe(ShippingPolicy p) {
        return "base=" + p.getBaseShippingFee() + ", free=" + p.getFreeShippingAmount()
                + ", jeju=" + p.getJejuExtraFee() + ", remote=" + p.getRemoteAreaExtraFee()
                + ", return=" + p.getReturnShippingFee();
    }

    private static ShippingPolicyResponse toResponse(ShippingPolicy p) {
        return new ShippingPolicyResponse(
                p.getBaseShippingFee(),
                p.getFreeShippingAmount(),
                p.getJejuExtraFee(),
                p.getRemoteAreaExtraFee(),
                p.getReturnShippingFee(),
                p.getUpdatedAt());
    }

    private static ExtraAreaResponse toResponse(ShippingExtraArea area) {
        return new ExtraAreaResponse(
                area.getAreaId(), area.getAreaType(), area.getPostcodeFrom(), area.getPostcodeTo(), area.getNote());
    }
}
