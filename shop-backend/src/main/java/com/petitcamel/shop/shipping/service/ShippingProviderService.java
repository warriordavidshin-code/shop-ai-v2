package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.ShippingOperationStatus;
import com.petitcamel.shop.shipping.domain.ShippingOperationType;
import com.petitcamel.shop.shipping.domain.ShippingProvider;
import com.petitcamel.shop.shipping.dto.ConnectionTestResponse;
import com.petitcamel.shop.shipping.dto.ShippingIntegrationStatus;
import com.petitcamel.shop.shipping.dto.ShippingProviderResponse;
import com.petitcamel.shop.shipping.dto.ShippingProviderUpdateRequest;
import com.petitcamel.shop.shipping.provider.ShippingProviderClient;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.ShippingProviderRegistry;
import com.petitcamel.shop.shipping.repository.ShippingProviderRepository;
import com.petitcamel.shop.shipping.support.ShippingLogMasker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin view of the shipping vendors: capability switches, connection test and overall integration status. */
@Service
public class ShippingProviderService {

    private final ShippingProviderRepository repository;
    private final ShippingProviderRegistry registry;
    private final ShippingOperationService operationService;
    private final AuditLogService auditLogService;
    private final ShippingProperties properties;

    public ShippingProviderService(
            ShippingProviderRepository repository,
            ShippingProviderRegistry registry,
            ShippingOperationService operationService,
            AuditLogService auditLogService,
            ShippingProperties properties) {
        this.repository = repository;
        this.registry = registry;
        this.operationService = operationService;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<ShippingProviderResponse> list() {
        Map<ProviderCapability, String> active = activeByCapability();
        return repository.findAllByOrderBySortOrderAscShippingProviderIdAsc().stream()
                .map(row -> toResponse(row, active))
                .toList();
    }

    @Transactional
    public ShippingProviderResponse update(String code, ShippingProviderUpdateRequest request, Long actorMemberId) {
        ShippingProvider row = requireProvider(code);
        if (row.isManual() && Boolean.FALSE.equals(request.enabled())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "수동 처리(MANUAL)는 다른 업체를 쓸 수 없을 때의 대체 수단이므로 끌 수 없습니다.");
        }
        if (request.enabled() != null) {
            row.setEnabled(request.enabled());
        }
        if (request.trackingEnabled() != null) {
            row.setTrackingEnabled(request.trackingEnabled());
        }
        if (request.waybillEnabled() != null) {
            row.setWaybillEnabled(request.waybillEnabled());
        }
        if (request.pickupEnabled() != null) {
            row.setPickupEnabled(request.pickupEnabled());
        }
        if (request.returnPickupEnabled() != null) {
            row.setReturnPickupEnabled(request.returnPickupEnabled());
        }
        repository.saveAndFlush(row);
        auditLogService.record(actorMemberId, "SHIPPING_PROVIDER_UPDATE", "SHIPPING_PROVIDER", row.getCode(),
                "enabled=" + row.isEnabled() + ", tracking=" + row.isTrackingEnabled() + ", waybill=" + row.isWaybillEnabled()
                        + ", pickup=" + row.isPickupEnabled() + ", returnPickup=" + row.isReturnPickupEnabled());
        return toResponse(row, activeByCapability());
    }

    /** Cheap authenticated call to the vendor; always logged in {@code shipping_api_operation}. */
    public ConnectionTestResponse testConnection(String code, Long actorMemberId) {
        String providerCode = code.trim().toUpperCase(Locale.ROOT);
        ShippingProviderClient client = registry.client(providerCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배송 API 업체를 찾을 수 없습니다."));
        ShippingOperationService.Request request = new ShippingOperationService.Request(
                providerCode, ShippingOperationType.CONNECTION_TEST, null, null, null, null);
        ConnectionTestResponse response;
        if (!client.isConfigured()) {
            response = new ConnectionTestResponse(providerCode, false,
                    "API 키 또는 접속 주소가 설정되지 않았습니다. 서버 환경변수(.env)를 확인해 주세요.");
            operationService.record(request, ShippingOperationStatus.FAILED,
                    new ShippingProviderException(response.message(), null, "NOT_CONFIGURED", false), null);
        } else {
            try {
                String message = client.testConnection();
                response = new ConnectionTestResponse(providerCode, true, message);
                operationService.record(request, ShippingOperationStatus.SUCCEEDED, null, message);
            } catch (ShippingProviderException ex) {
                String message = ShippingLogMasker.scrub(
                        ex.getMessage() == null ? "연결에 실패했습니다." : ex.getMessage(), properties.getApiKey());
                response = new ConnectionTestResponse(providerCode, false, message);
                operationService.record(request, ShippingOperationStatus.FAILED,
                        new ShippingProviderException(message, ex.getHttpStatus(), ex.getErrorCode(), false), null);
            }
        }
        auditLogService.record(actorMemberId, "SHIPPING_PROVIDER_TEST", "SHIPPING_PROVIDER", providerCode,
                "success=" + response.success());
        return response;
    }

    @Transactional(readOnly = true)
    public ShippingIntegrationStatus integrationStatus() {
        Map<ProviderCapability, String> active = activeByCapability();
        String tracking = active.get(ProviderCapability.TRACKING);
        ShippingProperties.Tracking t = properties.getTracking();
        return new ShippingIntegrationStatus(
                registry.preferredProvider(),
                tracking == null ? ShippingProvider.MANUAL : tracking,
                tracking != null,
                properties.getApiKey() != null && !properties.getApiKey().isBlank(),
                active.get(ProviderCapability.PICKUP),
                active.containsKey(ProviderCapability.WAYBILL),
                active.get(ProviderCapability.WAYBILL),
                active.get(ProviderCapability.RETURN_PICKUP),
                t.isSchedulerEnabled(),
                t.getCron(),
                t.getCacheMinutes());
    }

    private Map<ProviderCapability, String> activeByCapability() {
        Map<ProviderCapability, String> active = new EnumMap<>(ProviderCapability.class);
        for (ProviderCapability capability : ProviderCapability.values()) {
            registry.resolve(capability).ifPresent(p -> active.put(capability, p.code()));
        }
        return active;
    }

    private ShippingProviderResponse toResponse(ShippingProvider row, Map<ProviderCapability, String> active) {
        ShippingProviderClient client = registry.client(row.getCode()).orElse(null);
        List<String> supported = client == null ? List.of() : Arrays.stream(ProviderCapability.values())
                .filter(client::supports)
                .map(Enum::name)
                .toList();
        List<String> serving = active.entrySet().stream()
                .filter(e -> e.getValue().equals(row.getCode()))
                .map(e -> e.getKey().name())
                .toList();
        return new ShippingProviderResponse(
                row.getCode(),
                row.getName(),
                row.isEnabled(),
                row.isTrackingEnabled(),
                row.isWaybillEnabled(),
                row.isPickupEnabled(),
                row.isReturnPickupEnabled(),
                client != null && client.isConfigured(),
                row.getCode().equals(registry.preferredProvider()),
                supported,
                serving);
    }

    private ShippingProvider requireProvider(String code) {
        return repository.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배송 API 업체를 찾을 수 없습니다."));
    }
}
