package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentTrackingEvent;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.domain.ShippingOperationStatus;
import com.petitcamel.shop.shipping.domain.ShippingOperationType;
import com.petitcamel.shop.shipping.dto.ShipmentActionResponse;
import com.petitcamel.shop.shipping.dto.TrackingResult;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.ShippingProviderRegistry;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import com.petitcamel.shop.shipping.repository.ShipmentRepository;
import com.petitcamel.shop.shipping.repository.ShipmentTrackingEventRepository;
import com.petitcamel.shop.shipping.support.ShippingLogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Courier tracking sync. The vendor call happens outside any DB transaction; results are applied in a short
 * transaction afterwards. Vendor events are appended only when new (deduplicated by the vendor's event id, or by a
 * hash of the event content), so repeated polling never duplicates the timeline. A shipment checked within
 * {@code shipping.tracking.cache-minutes} is served from the DB. Vendor failures are recorded on the shipment and
 * in {@code shipping_api_operation}, and never propagate to the caller.
 */
@Service
public class TrackingService {

    public static final String TEMPORARY_FAILURE_MESSAGE =
            "배송정보를 일시적으로 조회할 수 없습니다. 잠시 후 다시 확인해주세요.";

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    public enum RefreshOutcome { UPDATED, NOT_FOUND, UNSUPPORTED_COMPANY, FAILED, CONFLICT, SKIPPED }

    private record Snapshot(Long shipmentId, Long orderId, ShipmentType type, Long companyId,
                            String trackingNumber, ShipmentStatus status) {
    }

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository eventRepository;
    private final OrderEntityRepository orderRepository;
    private final ShippingProviderRegistry providerRegistry;
    private final DeliveryCompanyService deliveryCompanyService;
    private final ShipmentService shipmentService;
    private final ShippingOperationService operationService;
    private final ShipmentViewAssembler assembler;
    private final ShippingProperties properties;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;
    private final Clock clock;

    public TrackingService(
            ShipmentRepository shipmentRepository,
            ShipmentTrackingEventRepository eventRepository,
            OrderEntityRepository orderRepository,
            ShippingProviderRegistry providerRegistry,
            DeliveryCompanyService deliveryCompanyService,
            ShipmentService shipmentService,
            ShippingOperationService operationService,
            ShipmentViewAssembler assembler,
            ShippingProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.shipmentRepository = shipmentRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.providerRegistry = providerRegistry;
        this.deliveryCompanyService = deliveryCompanyService;
        this.shipmentService = shipmentService;
        this.operationService = operationService;
        this.assembler = assembler;
        this.properties = properties;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.clock = clock;
    }

    /** Customer tracking. Refreshes from the courier only when the cached data is stale. */
    public TrackingResult getTrackingForMember(Long memberId, Long orderId, ShipmentType type) {
        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> Objects.equals(o.getMemberId(), memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
        Shipment shipment = latest(orderId, type);
        if (shipment != null && isStale(shipment)) {
            try {
                refresh(shipment.getShipmentId());
            } catch (RuntimeException ex) {
                log.warn("[SHIPPING] tracking refresh skipped orderId={} error={}", orderId, ex.getClass().getSimpleName());
            }
        }
        return readTx.execute(status -> buildResult(order, type));
    }

    /** Admin "배송조회 새로고침": bypasses the cache but not the short anti-hammering interval. */
    public ShipmentActionResponse adminRefresh(Long orderId, ShipmentType type) {
        Shipment shipment = latest(orderId, type);
        if (shipment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "배송 정보가 없습니다.");
        }
        if (!providerRegistry.externalTrackingEnabled()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "외부 배송조회 연동이 설정되지 않았습니다. (배송 API 업체 설정 / SHIPPING_API_KEY 확인)");
        }
        if (!shipment.hasTrackingNumber()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "송장번호를 먼저 등록해 주세요.");
        }
        Instant last = shipment.getLastTrackingCheckedAt();
        int minSeconds = properties.getTracking().getForceRefreshMinSeconds();
        if (last != null && last.isAfter(clock.instant().minusSeconds(minSeconds))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "방금 조회했습니다. 잠시 후 다시 시도해 주세요.");
        }
        RefreshOutcome outcome = refresh(shipment.getShipmentId());
        String message = switch (outcome) {
            case UPDATED -> "배송정보를 갱신했습니다.";
            case NOT_FOUND -> "택배사에 아직 조회 정보가 없습니다. 집하 후 다시 확인해 주세요.";
            case UNSUPPORTED_COMPANY -> "이 택배사는 배송조회 API 코드가 등록되어 있지 않습니다.";
            case CONFLICT -> "다른 작업과 겹쳤습니다. 다시 시도해 주세요.";
            case SKIPPED -> "조회 대상 상태가 아닙니다.";
            case FAILED -> TEMPORARY_FAILURE_MESSAGE;
        };
        return readTx.execute(status -> new ShipmentActionResponse(
                outcome != RefreshOutcome.FAILED && outcome != RefreshOutcome.CONFLICT,
                message,
                shipmentRepository.findById(shipment.getShipmentId())
                        .map(s -> assembler.toView(s, true))
                        .orElse(null)));
    }

    /** Scheduler entry point: refreshes moving shipments whose cache has expired. */
    public int refreshDueShipments() {
        ShippingProviderRegistry.ActiveProvider provider = providerRegistry.resolve(ProviderCapability.TRACKING).orElse(null);
        if (provider == null) {
            return 0;
        }
        Instant checkedBefore = clock.instant().minus(Duration.ofMinutes(properties.getTracking().getCacheMinutes()));
        List<Long> ids = readTx.execute(status -> shipmentRepository.findDueForTracking(
                ShipmentStatus.TRACKABLE, checkedBefore, PageRequest.of(0, properties.getTracking().getBatchSize())));
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int updated = 0;
        int failed = 0;
        for (Long id : ids) {
            try {
                RefreshOutcome outcome = refresh(id);
                if (outcome == RefreshOutcome.UPDATED) {
                    updated++;
                } else if (outcome == RefreshOutcome.FAILED) {
                    failed++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("[SHIPPING] scheduled refresh error shipmentId={} error={}", id, ex.getClass().getSimpleName());
            }
        }
        log.info("[SHIPPING] scheduled tracking provider={} total={} updated={} failed={}",
                provider.code(), ids.size(), updated, failed);
        return updated;
    }

    public RefreshOutcome refresh(Long shipmentId) {
        Snapshot snap = readTx.execute(status -> shipmentRepository.findById(shipmentId)
                .map(s -> new Snapshot(s.getShipmentId(), s.getOrderId(), s.getShipmentType(), s.getDeliveryCompanyId(),
                        s.getTrackingNumber(), s.getStatus()))
                .orElse(null));
        if (snap == null || snap.trackingNumber() == null || snap.companyId() == null
                || !ShipmentStatus.TRACKABLE.contains(snap.status())) {
            return RefreshOutcome.SKIPPED;
        }
        ShippingProviderRegistry.ActiveProvider provider = providerRegistry.resolve(ProviderCapability.TRACKING).orElse(null);
        if (provider == null) {
            return RefreshOutcome.SKIPPED;
        }
        String maskedNumber = ShippingLogMasker.maskTrackingNumber(snap.trackingNumber());

        String externalCode = deliveryCompanyService.externalCode(snap.companyId(), provider.id()).orElse(null);
        if (externalCode == null) {
            markChecked(snap, null, false);
            log.info("[SHIPPING] provider={} orderId={} companyId={} trackingNumber={} status={} result=UNSUPPORTED_COMPANY",
                    provider.code(), snap.orderId(), snap.companyId(), maskedNumber, snap.status());
            return RefreshOutcome.UNSUPPORTED_COMPANY;
        }

        TrackingResponse response;
        try {
            response = provider.client().tracking(externalCode, snap.trackingNumber());
        } catch (RuntimeException ex) {
            String error = ShippingLogMasker.scrub(
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), properties.getApiKey());
            markChecked(snap, error, true);
            ShippingProviderException providerError = ex instanceof ShippingProviderException spe ? spe
                    : new ShippingProviderException(error, null, ex.getClass().getSimpleName(), false);
            recordFailure(provider.code(), snap, providerError, error);
            log.warn("[SHIPPING] provider={} orderId={} companyId={} trackingNumber={} status={} result=FAILED error={}",
                    provider.code(), snap.orderId(), snap.companyId(), maskedNumber, snap.status(), error);
            return RefreshOutcome.FAILED;
        }

        try {
            Boolean applied = writeTx.execute(status -> apply(snap, provider.id(), response));
            RefreshOutcome outcome = !Boolean.TRUE.equals(applied)
                    ? RefreshOutcome.SKIPPED
                    : response.found() ? RefreshOutcome.UPDATED : RefreshOutcome.NOT_FOUND;
            log.info("[SHIPPING] provider={} orderId={} companyId={} trackingNumber={} status={} result={}",
                    provider.code(), snap.orderId(), snap.companyId(), maskedNumber,
                    response.status() == null ? snap.status() : response.status(), outcome);
            return outcome;
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.info("[SHIPPING] provider={} orderId={} companyId={} trackingNumber={} result=CONFLICT",
                    provider.code(), snap.orderId(), snap.companyId(), maskedNumber);
            return RefreshOutcome.CONFLICT;
        }
    }

    /** Content hash used to recognise a vendor event seen before when the vendor gives no event id. */
    static String rawHash(TrackingResponse.Event event) {
        String source = (event.time() == null ? "" : String.valueOf(event.time().toEpochMilli()))
                + "|" + nullToEmpty(event.providerStatus())
                + "|" + nullToEmpty(event.location())
                + "|" + nullToEmpty(event.description());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Shipment latest(Long orderId, ShipmentType type) {
        return readTx.execute(status -> shipmentRepository
                .findFirstByOrderIdAndShipmentTypeOrderByShipmentIdDesc(orderId, type).orElse(null));
    }

    private boolean isStale(Shipment shipment) {
        if (!shipment.hasTrackingNumber() || !ShipmentStatus.TRACKABLE.contains(shipment.getStatus())
                || !providerRegistry.externalTrackingEnabled()) {
            return false;
        }
        Instant last = shipment.getLastTrackingCheckedAt();
        return last == null
                || last.isBefore(clock.instant().minus(Duration.ofMinutes(properties.getTracking().getCacheMinutes())));
    }

    private Boolean apply(Snapshot snap, Long providerId, TrackingResponse response) {
        Shipment shipment = shipmentRepository.findById(snap.shipmentId()).orElse(null);
        if (shipment == null
                || !Objects.equals(shipment.getTrackingNumber(), snap.trackingNumber())
                || !Objects.equals(shipment.getDeliveryCompanyId(), snap.companyId())) {
            return false;
        }
        Instant now = clock.instant();
        shipment.setLastTrackingCheckedAt(now);
        shipment.setLastTrackingError(null);
        shipment.setTrackingFailCount(0);
        if (shipment.getShippingProviderId() == null) {
            shipment.setShippingProviderId(providerId);
        }
        if (response.found()) {
            appendNewEvents(shipment.getShipmentId(), response.events(), now);
            shipmentService.applyTrackingStatus(shipment, response.status(), eventTimeFor(response, now));
        }
        shipmentRepository.save(shipment);
        return true;
    }

    private void appendNewEvents(Long shipmentId, List<TrackingResponse.Event> events, Instant now) {
        Set<String> seenHashes = new HashSet<>(eventRepository.findRawHashes(shipmentId));
        Set<String> seenIds = new HashSet<>(eventRepository.findExternalEventIds(shipmentId));
        for (TrackingResponse.Event e : events) {
            String externalId = truncate(e.externalEventId(), 100);
            String hash = rawHash(e);
            if ((externalId != null && !seenIds.add(externalId)) || !seenHashes.add(hash)) {
                continue;
            }
            eventRepository.save(ShipmentTrackingEvent.provider(
                    shipmentId,
                    e.time() == null ? now : e.time(),
                    externalId,
                    truncate(e.providerStatus(), 100),
                    e.status(),
                    truncate(e.location(), 100),
                    truncate(e.description() == null || e.description().isBlank() ? "배송 상태 변경" : e.description(), 300),
                    hash));
        }
    }

    private void markChecked(Snapshot snap, String error, boolean failure) {
        try {
            writeTx.executeWithoutResult(status -> shipmentRepository.findById(snap.shipmentId()).ifPresent(s -> {
                s.setLastTrackingCheckedAt(clock.instant());
                s.setLastTrackingError(truncate(error, 300));
                s.setTrackingFailCount(failure ? s.getTrackingFailCount() + 1 : 0);
                shipmentRepository.save(s);
            }));
        } catch (ObjectOptimisticLockingFailureException ignored) {
            // A concurrent update already touched the shipment; the next poll records the result.
        }
    }

    private void recordFailure(String providerCode, Snapshot snap, ShippingProviderException error, String scrubbed) {
        try {
            ShippingProviderException safe = new ShippingProviderException(
                    scrubbed, error.getHttpStatus(), error.getErrorCode(), error.isOutcomeUnknown());
            operationService.record(new ShippingOperationService.Request(
                            providerCode, ShippingOperationType.TRACKING, snap.orderId(), snap.shipmentId(), null, null),
                    ShippingOperationStatus.FAILED, safe, null);
        } catch (RuntimeException ex) {
            log.warn("[SHIPPING] failed to record tracking failure shipmentId={} error={}",
                    snap.shipmentId(), ex.getClass().getSimpleName());
        }
    }

    private TrackingResult buildResult(OrderEntity order, ShipmentType type) {
        boolean external = providerRegistry.externalTrackingEnabled();
        Shipment shipment = shipmentRepository
                .findFirstByOrderIdAndShipmentTypeOrderByShipmentIdDesc(order.getOrderId(), type).orElse(null);
        if (shipment == null) {
            return new TrackingResult(order.getOrderId(), order.getOrderNo(), order.getOrderStatus().name(), type.name(),
                    null, null, null, null, null, null, null, null, null, external, null, List.of());
        }
        ShipmentViewAssembler.Refs refs = assembler.refs();
        String message = external && shipment.getLastTrackingError() != null ? TEMPORARY_FAILURE_MESSAGE : null;
        return new TrackingResult(
                order.getOrderId(),
                order.getOrderNo(),
                order.getOrderStatus().name(),
                type.name(),
                refs.companyCode(shipment.getDeliveryCompanyId()),
                refs.companyName(shipment.getDeliveryCompanyId()),
                shipment.getTrackingNumber(),
                refs.trackingUrl(shipment.getDeliveryCompanyId(), shipment.getTrackingNumber()),
                shipment.getStatus().name(),
                shipment.getStatus().labelFor(shipment.getShipmentType()),
                shipment.getShippedAt(),
                shipment.getDeliveredAt(),
                shipment.getLastTrackingCheckedAt(),
                external,
                message,
                assembler.events(shipment.getShipmentId()));
    }

    private static Instant eventTimeFor(TrackingResponse response, Instant fallback) {
        if (response.status() == null) {
            return fallback;
        }
        return response.events().stream()
                .filter(e -> e.status() == response.status() && e.time() != null)
                .map(TrackingResponse.Event::time)
                .reduce((first, second) -> second)
                .orElse(fallback);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
