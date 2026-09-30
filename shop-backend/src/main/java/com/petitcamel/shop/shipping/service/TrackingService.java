package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentTrackingEvent;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.domain.TrackingEventSource;
import com.petitcamel.shop.shipping.dto.ShipmentActionResponse;
import com.petitcamel.shop.shipping.dto.TrackingResult;
import com.petitcamel.shop.shipping.provider.ShippingProvider;
import com.petitcamel.shop.shipping.provider.ShippingProviderRegistry;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import com.petitcamel.shop.shipping.provider.TrackingStatusMapper;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Courier tracking sync. The provider call happens outside any DB transaction; results are applied in a
 * short transaction afterwards. A shipment checked within {@code shipping.tracking.cache-minutes} is served
 * from the DB. Provider failures are recorded on the shipment and never propagate to the caller.
 */
@Service
public class TrackingService {

    public static final String TEMPORARY_FAILURE_MESSAGE =
            "배송정보를 일시적으로 조회할 수 없습니다. 잠시 후 다시 확인해주세요.";

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    public enum RefreshOutcome { UPDATED, NOT_FOUND, UNSUPPORTED_COMPANY, FAILED, CONFLICT, SKIPPED }

    private record Snapshot(Long shipmentId, Long orderId, ShipmentType type, String company,
                            String trackingNumber, ShipmentStatus status) {
    }

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository eventRepository;
    private final OrderEntityRepository orderRepository;
    private final ShippingProviderRegistry providerRegistry;
    private final DeliveryCompanyService deliveryCompanyService;
    private final ShipmentService shipmentService;
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
        Shipment shipment = shipmentRepository.findByOrderIdAndShipmentType(orderId, type).orElse(null);
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
        Shipment shipment = shipmentRepository.findByOrderIdAndShipmentType(orderId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배송 정보가 없습니다."));
        if (!providerRegistry.externalTrackingEnabled()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "외부 배송조회 연동이 설정되지 않았습니다. (SHIPPING_PROVIDER / SHIPPING_API_KEY 확인)");
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

    /** Scheduler entry point: refreshes in-transit shipments whose cache has expired. */
    public int refreshDueShipments() {
        if (!providerRegistry.externalTrackingEnabled()) {
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
                providerRegistry.active().name(), ids.size(), updated, failed);
        return updated;
    }

    public RefreshOutcome refresh(Long shipmentId) {
        Snapshot snap = readTx.execute(status -> shipmentRepository.findById(shipmentId)
                .map(s -> new Snapshot(s.getShipmentId(), s.getOrderId(), s.getShipmentType(), s.getDeliveryCompany(),
                        s.getTrackingNumber(), s.getShipmentStatus()))
                .orElse(null));
        if (snap == null || snap.trackingNumber() == null || snap.company() == null
                || !ShipmentStatus.TRACKABLE.contains(snap.status()) || !providerRegistry.externalTrackingEnabled()) {
            return RefreshOutcome.SKIPPED;
        }
        ShippingProvider provider = providerRegistry.active();
        String maskedNumber = ShippingLogMasker.maskTrackingNumber(snap.trackingNumber());

        String providerCode = deliveryCompanyService.providerCode(snap.company(), provider.name()).orElse(null);
        if (providerCode == null) {
            markChecked(snap, null, false);
            log.info("[SHIPPING] provider={} orderId={} company={} trackingNumber={} status={} result=UNSUPPORTED_COMPANY",
                    provider.name(), snap.orderId(), snap.company(), maskedNumber, snap.status());
            return RefreshOutcome.UNSUPPORTED_COMPANY;
        }

        TrackingResponse response;
        try {
            response = provider.tracking(providerCode, snap.trackingNumber());
        } catch (RuntimeException ex) {
            String error = ShippingLogMasker.scrub(
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), properties.getApiKey());
            markChecked(snap, error, true);
            log.warn("[SHIPPING] provider={} orderId={} company={} trackingNumber={} status={} result=FAILED error={}",
                    provider.name(), snap.orderId(), snap.company(), maskedNumber, snap.status(), error);
            return RefreshOutcome.FAILED;
        }

        try {
            Boolean applied = writeTx.execute(status -> apply(snap, response));
            RefreshOutcome outcome = !Boolean.TRUE.equals(applied)
                    ? RefreshOutcome.SKIPPED
                    : response.found() ? RefreshOutcome.UPDATED : RefreshOutcome.NOT_FOUND;
            log.info("[SHIPPING] provider={} orderId={} company={} trackingNumber={} status={} result={}",
                    provider.name(), snap.orderId(), snap.company(), maskedNumber,
                    response.status() == null ? snap.status() : response.status(), outcome);
            return outcome;
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.info("[SHIPPING] provider={} orderId={} company={} trackingNumber={} result=CONFLICT",
                    provider.name(), snap.orderId(), snap.company(), maskedNumber);
            return RefreshOutcome.CONFLICT;
        }
    }

    private boolean isStale(Shipment shipment) {
        if (!providerRegistry.externalTrackingEnabled() || !shipment.hasTrackingNumber()
                || !ShipmentStatus.TRACKABLE.contains(shipment.getShipmentStatus())) {
            return false;
        }
        Instant last = shipment.getLastTrackingCheckedAt();
        return last == null
                || last.isBefore(clock.instant().minus(Duration.ofMinutes(properties.getTracking().getCacheMinutes())));
    }

    private Boolean apply(Snapshot snap, TrackingResponse response) {
        Shipment shipment = shipmentRepository.findById(snap.shipmentId()).orElse(null);
        if (shipment == null
                || !Objects.equals(shipment.getTrackingNumber(), snap.trackingNumber())
                || !Objects.equals(shipment.getDeliveryCompany(), snap.company())) {
            return false;
        }
        Instant now = clock.instant();
        shipment.setLastTrackingCheckedAt(now);
        shipment.setLastTrackingError(null);
        shipment.setTrackingFailCount(0);
        if (response.found()) {
            boolean isReturn = shipment.getShipmentType() == ShipmentType.RETURN;
            eventRepository.deleteByShipmentIdAndSource(shipment.getShipmentId(), TrackingEventSource.PROVIDER);
            for (TrackingResponse.Event e : response.events()) {
                ShipmentTrackingEvent event = new ShipmentTrackingEvent();
                event.setShipmentId(shipment.getShipmentId());
                event.setSource(TrackingEventSource.PROVIDER);
                event.setEventTime(e.time() == null ? now : e.time());
                event.setLocation(truncate(e.location(), 100));
                event.setDescription(truncate(e.description() == null || e.description().isBlank()
                        ? "배송 상태 변경" : e.description(), 300));
                event.setStatus(isReturn ? TrackingStatusMapper.toReturnStatus(e.status()) : e.status());
                event.setCreatedAt(now);
                eventRepository.save(event);
            }
            ShipmentStatus target = isReturn ? TrackingStatusMapper.toReturnStatus(response.status()) : response.status();
            shipmentService.applyTrackingStatus(shipment, target, eventTimeFor(response, now));
        }
        shipmentRepository.save(shipment);
        return true;
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

    private TrackingResult buildResult(OrderEntity order, ShipmentType type) {
        boolean external = providerRegistry.externalTrackingEnabled();
        Shipment shipment = shipmentRepository.findByOrderIdAndShipmentType(order.getOrderId(), type).orElse(null);
        if (shipment == null) {
            return new TrackingResult(order.getOrderId(), order.getOrderNo(), order.getOrderStatus().name(), type.name(),
                    null, null, null, null, null, null, null, null, null, external, null, List.of());
        }
        Map<String, DeliveryCompany> companies = assembler.companies();
        String message = external && shipment.getLastTrackingError() != null ? TEMPORARY_FAILURE_MESSAGE : null;
        return new TrackingResult(
                order.getOrderId(),
                order.getOrderNo(),
                order.getOrderStatus().name(),
                type.name(),
                shipment.getDeliveryCompany(),
                DeliveryCompanyService.companyName(companies, shipment.getDeliveryCompany()),
                shipment.getTrackingNumber(),
                DeliveryCompanyService.trackingUrl(companies, shipment.getDeliveryCompany(), shipment.getTrackingNumber()),
                shipment.getShipmentStatus().name(),
                shipment.getShipmentStatus().getLabel(),
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
