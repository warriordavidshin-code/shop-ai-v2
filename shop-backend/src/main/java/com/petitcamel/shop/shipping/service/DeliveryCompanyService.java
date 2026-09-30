package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.DeliveryCompanyProviderCode;
import com.petitcamel.shop.shipping.domain.ShippingProvider;
import com.petitcamel.shop.shipping.dto.DeliveryCompanyResponse;
import com.petitcamel.shop.shipping.repository.DeliveryCompanyProviderCodeRepository;
import com.petitcamel.shop.shipping.repository.DeliveryCompanyRepository;
import com.petitcamel.shop.shipping.repository.ShippingProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Couriers and their per-vendor codes. */
@Service
public class DeliveryCompanyService {

    public static final String TRACKING_NUMBER_PLACEHOLDER = "{trackingNumber}";

    private final DeliveryCompanyRepository companyRepository;
    private final DeliveryCompanyProviderCodeRepository codeRepository;
    private final ShippingProviderRepository providerRepository;
    private final AuditLogService auditLogService;

    public DeliveryCompanyService(
            DeliveryCompanyRepository companyRepository,
            DeliveryCompanyProviderCodeRepository codeRepository,
            ShippingProviderRepository providerRepository,
            AuditLogService auditLogService) {
        this.companyRepository = companyRepository;
        this.codeRepository = codeRepository;
        this.providerRepository = providerRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<DeliveryCompanyResponse> listAll() {
        Map<Long, String> providerCodes = providerRepository.findAll().stream()
                .collect(Collectors.toMap(ShippingProvider::getShippingProviderId, ShippingProvider::getCode));
        Map<Long, Map<String, String>> codesByCompany = codeRepository.findAll().stream()
                .filter(c -> providerCodes.containsKey(c.getShippingProviderId()))
                .collect(Collectors.groupingBy(
                        DeliveryCompanyProviderCode::getDeliveryCompanyId,
                        Collectors.toMap(c -> providerCodes.get(c.getShippingProviderId()),
                                DeliveryCompanyProviderCode::getExternalCompanyCode, (a, b) -> a, TreeMap::new)));
        return companyRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
                .map(c -> new DeliveryCompanyResponse(
                        c.getCode(),
                        c.getName(),
                        c.getTrackingUrlTemplate(),
                        c.isEnabled(),
                        c.getSortOrder(),
                        codesByCompany.getOrDefault(c.getDeliveryCompanyId(), Map.of())))
                .toList();
    }

    /** Accepts a courier code ("HANJIN") or display name ("한진택배", "한진") and requires it to be enabled. */
    @Transactional(readOnly = true)
    public DeliveryCompany resolveEnabled(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "택배사를 선택해 주세요.");
        }
        String input = codeOrName.trim();
        List<DeliveryCompany> companies = companyRepository.findAll();
        String compactInput = compact(input);
        DeliveryCompany found = companies.stream()
                .filter(c -> c.getCode().equalsIgnoreCase(input))
                .findFirst()
                .or(() -> companies.stream()
                        .filter(c -> compact(c.getName()).equals(compactInput))
                        .findFirst())
                .or(() -> companies.stream()
                        .filter(c -> compactInput.length() >= 2 && compact(c.getName()).startsWith(compactInput))
                        .findFirst())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "지원하지 않는 택배사입니다: " + input));
        if (!found.isEnabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "사용 중지된 택배사입니다: " + found.getName());
        }
        return found;
    }

    /** The courier's code in a vendor's code system, e.g. (HANJIN, SWEETTRACKER) -> "05". */
    @Transactional(readOnly = true)
    public Optional<String> externalCode(Long deliveryCompanyId, Long shippingProviderId) {
        if (deliveryCompanyId == null || shippingProviderId == null) {
            return Optional.empty();
        }
        return codeRepository.findByDeliveryCompanyIdAndShippingProviderId(deliveryCompanyId, shippingProviderId)
                .map(DeliveryCompanyProviderCode::getExternalCompanyCode);
    }

    @Transactional(readOnly = true)
    public Map<Long, DeliveryCompany> companiesById() {
        return companyRepository.findAll().stream()
                .collect(Collectors.toMap(DeliveryCompany::getDeliveryCompanyId, Function.identity()));
    }

    @Transactional
    public DeliveryCompanyResponse setEnabled(String code, boolean enabled, Long actorMemberId) {
        DeliveryCompany company = requireByCode(code);
        company.setEnabled(enabled);
        companyRepository.save(company);
        auditLogService.record(actorMemberId, "DELIVERY_COMPANY_UPDATE", "DELIVERY_COMPANY", company.getCode(),
                "enabled=" + enabled);
        return find(company.getCode());
    }

    /** Sets (or with a blank code removes) the courier's code for a vendor. */
    @Transactional
    public DeliveryCompanyResponse updateProviderCode(
            String companyCode, String providerCode, String externalCompanyCode, Long actorMemberId) {
        DeliveryCompany company = requireByCode(companyCode);
        ShippingProvider provider = providerRepository.findByCode(providerCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배송 API 업체를 찾을 수 없습니다."));
        Optional<DeliveryCompanyProviderCode> existing = codeRepository
                .findByDeliveryCompanyIdAndShippingProviderId(company.getDeliveryCompanyId(), provider.getShippingProviderId());
        String value = externalCompanyCode == null ? "" : externalCompanyCode.trim();
        if (value.isEmpty()) {
            existing.ifPresent(codeRepository::delete);
        } else {
            DeliveryCompanyProviderCode mapping = existing.orElseGet(DeliveryCompanyProviderCode::new);
            mapping.setDeliveryCompanyId(company.getDeliveryCompanyId());
            mapping.setShippingProviderId(provider.getShippingProviderId());
            mapping.setExternalCompanyCode(value);
            codeRepository.save(mapping);
        }
        auditLogService.record(actorMemberId, "DELIVERY_COMPANY_PROVIDER_CODE", "DELIVERY_COMPANY", company.getCode(),
                "provider=" + provider.getCode() + ", code=" + (value.isEmpty() ? "(removed)" : value));
        return find(company.getCode());
    }

    private DeliveryCompanyResponse find(String code) {
        return listAll().stream()
                .filter(c -> c.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private DeliveryCompany requireByCode(String code) {
        return companyRepository.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "택배사를 찾을 수 없습니다."));
    }

    public static String companyCode(Map<Long, DeliveryCompany> companies, Long id) {
        DeliveryCompany company = id == null ? null : companies.get(id);
        return company == null ? null : company.getCode();
    }

    public static String companyName(Map<Long, DeliveryCompany> companies, Long id) {
        DeliveryCompany company = id == null ? null : companies.get(id);
        return company == null ? null : company.getName();
    }

    /** Courier's public tracking page for the invoice, or null when the courier has no URL template. */
    public static String trackingUrl(Map<Long, DeliveryCompany> companies, Long id, String trackingNumber) {
        if (id == null || trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }
        DeliveryCompany company = companies.get(id);
        if (company == null || company.getTrackingUrlTemplate() == null
                || !company.getTrackingUrlTemplate().contains(TRACKING_NUMBER_PLACEHOLDER)) {
            return null;
        }
        return company.getTrackingUrlTemplate().replace(TRACKING_NUMBER_PLACEHOLDER,
                URLEncoder.encode(trackingNumber, StandardCharsets.UTF_8));
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
