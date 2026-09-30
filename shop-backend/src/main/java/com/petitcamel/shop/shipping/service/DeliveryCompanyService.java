package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.DeliveryCompanyCode;
import com.petitcamel.shop.shipping.dto.DeliveryCompanyResponse;
import com.petitcamel.shop.shipping.repository.DeliveryCompanyCodeRepository;
import com.petitcamel.shop.shipping.repository.DeliveryCompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class DeliveryCompanyService {

    public static final String TRACKING_NUMBER_PLACEHOLDER = "{trackingNumber}";

    private final DeliveryCompanyRepository companyRepository;
    private final DeliveryCompanyCodeRepository codeRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public DeliveryCompanyService(
            DeliveryCompanyRepository companyRepository,
            DeliveryCompanyCodeRepository codeRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.companyRepository = companyRepository;
        this.codeRepository = codeRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeliveryCompanyResponse> listAll() {
        Map<String, Map<String, String>> codesByCompany = codeRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        DeliveryCompanyCode::getCompanyCode,
                        Collectors.toMap(DeliveryCompanyCode::getProvider, DeliveryCompanyCode::getProviderCode,
                                (a, b) -> a, TreeMap::new)));
        return companyRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
                .map(c -> new DeliveryCompanyResponse(
                        c.getCode(),
                        c.getCompanyName(),
                        c.getTrackingUrlTemplate(),
                        c.isEnabled(),
                        c.getSortOrder(),
                        codesByCompany.getOrDefault(c.getCode(), Map.of())))
                .toList();
    }

    /** Accepts an internal code ("HANJIN") or display name ("한진택배", "한진") and requires it to be enabled. */
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
                        .filter(c -> compact(c.getCompanyName()).equals(compactInput))
                        .findFirst())
                .or(() -> companies.stream()
                        .filter(c -> compactInput.length() >= 2 && compact(c.getCompanyName()).startsWith(compactInput))
                        .findFirst())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "지원하지 않는 택배사입니다: " + input));
        if (!found.isEnabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "사용 중지된 택배사입니다: " + found.getCompanyName());
        }
        return found;
    }

    @Transactional(readOnly = true)
    public Optional<String> providerCode(String companyCode, String provider) {
        if (companyCode == null || provider == null) {
            return Optional.empty();
        }
        return codeRepository.findByCompanyCodeAndProvider(companyCode, provider)
                .map(DeliveryCompanyCode::getProviderCode);
    }

    @Transactional(readOnly = true)
    public Map<String, DeliveryCompany> companiesByCode() {
        return companyRepository.findAll().stream()
                .collect(Collectors.toMap(DeliveryCompany::getCode, c -> c));
    }

    @Transactional
    public DeliveryCompanyResponse setEnabled(String code, boolean enabled, Long actorMemberId) {
        DeliveryCompany company = companyRepository.findById(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "택배사를 찾을 수 없습니다."));
        company.setEnabled(enabled);
        company.setUpdatedAt(clock.instant());
        companyRepository.save(company);
        auditLogService.record(actorMemberId, "DELIVERY_COMPANY_UPDATE", "DELIVERY_COMPANY", company.getCode(),
                "enabled=" + enabled);
        return listAll().stream()
                .filter(c -> c.code().equals(company.getCode()))
                .findFirst()
                .orElseThrow();
    }

    public static String companyName(Map<String, DeliveryCompany> companies, String code) {
        if (code == null) {
            return null;
        }
        DeliveryCompany company = companies.get(code);
        return company == null ? code : company.getCompanyName();
    }

    /** Courier's public tracking page for the invoice, or null when the courier has no URL template. */
    public static String trackingUrl(Map<String, DeliveryCompany> companies, String code, String trackingNumber) {
        if (code == null || trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }
        DeliveryCompany company = companies.get(code);
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
