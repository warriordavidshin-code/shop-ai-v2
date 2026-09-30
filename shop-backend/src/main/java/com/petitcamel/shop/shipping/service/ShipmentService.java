package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.ReturnRequest;
import com.petitcamel.shop.shipping.domain.ReturnStatus;
import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentTrackingEvent;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.domain.TrackingEventSource;
import com.petitcamel.shop.shipping.dto.BulkInvoiceRequest;
import com.petitcamel.shop.shipping.dto.BulkInvoiceResponse;
import com.petitcamel.shop.shipping.dto.ShipmentActionResponse;
import com.petitcamel.shop.shipping.dto.ShipmentView;
import com.petitcamel.shop.shipping.event.ShipmentStatusChangedEvent;
import com.petitcamel.shop.shipping.pickup.PickupRequest;
import com.petitcamel.shop.shipping.pickup.PickupResponse;
import com.petitcamel.shop.shipping.pickup.PickupService;
import com.petitcamel.shop.shipping.repository.ReturnRequestRepository;
import com.petitcamel.shop.shipping.repository.ShipmentRepository;
import com.petitcamel.shop.shipping.repository.ShipmentTrackingEventRepository;
import com.petitcamel.shop.shipping.support.ShippingLogMasker;
import com.petitcamel.shop.shipping.waybill.WaybillRequest;
import com.petitcamel.shop.shipping.waybill.WaybillResponse;
import com.petitcamel.shop.shipping.waybill.WaybillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shipment lifecycle: invoice registration, status transitions and keeping the order status in step.
 * All status changes go through {@link #transition} so timestamps, events, order sync and
 * notifications stay consistent regardless of whether an admin or the tracking sync triggered them.
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    /** Orders an admin can ship. */
    static final Set<OrderStatus> ACTIONABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);

    /** Orders that may own a delivery shipment (DELIVERED covers orders completed before this module). */
    static final Set<OrderStatus> SHIPMENT_ORDER_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    /** Delivery statuses an admin may set by hand (pickup requests have their own action). */
    static final Set<ShipmentStatus> MANUAL_TARGETS = EnumSet.of(
            ShipmentStatus.PREPARING, ShipmentStatus.READY, ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED);

    private static final Pattern TRACKING_NUMBER = Pattern.compile("^[A-Z0-9]{6,30}$");

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository eventRepository;
    private final OrderEntityRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final DeliveryCompanyService deliveryCompanyService;
    private final PickupService pickupService;
    private final WaybillService waybillService;
    private final ShipmentViewAssembler assembler;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate perRowTransaction;
    private final Clock clock;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            ShipmentTrackingEventRepository eventRepository,
            OrderEntityRepository orderRepository,
            ReturnRequestRepository returnRequestRepository,
            DeliveryCompanyService deliveryCompanyService,
            PickupService pickupService,
            WaybillService waybillService,
            ShipmentViewAssembler assembler,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.shipmentRepository = shipmentRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.deliveryCompanyService = deliveryCompanyService;
        this.pickupService = pickupService;
        this.waybillService = waybillService;
        this.assembler = assembler;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.perRowTransaction = new TransactionTemplate(transactionManager);
        this.perRowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ admin actions (delivery)

    @Transactional
    public ShipmentActionResponse registerInvoice(Long orderId, String deliveryCompany, String trackingNumber, Long actorId) {
        return registerInvoice(requireOrder(orderId), deliveryCompany, trackingNumber, actorId);
    }

    /** Registers each row in its own transaction so one bad row does not undo the others. */
    public BulkInvoiceResponse bulkRegister(List<BulkInvoiceRequest.Item> items, Long actorId) {
        List<BulkInvoiceResponse.Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int success = 0;
        for (int i = 0; i < items.size(); i++) {
            BulkInvoiceRequest.Item item = items.get(i);
            String orderNo = item.orderNumber() == null ? "" : item.orderNumber().trim();
            if (orderNo.isEmpty() || isBlank(item.deliveryCompany()) || isBlank(item.trackingNumber())) {
                rows.add(new BulkInvoiceResponse.Row(i + 1, orderNo, false, "주문번호/택배사/송장번호를 모두 입력해 주세요."));
                continue;
            }
            if (!seen.add(orderNo)) {
                rows.add(new BulkInvoiceResponse.Row(i + 1, orderNo, false, "같은 주문번호가 중복되었습니다."));
                continue;
            }
            try {
                ShipmentActionResponse result = perRowTransaction.execute(status -> {
                    OrderEntity order = orderRepository.findByOrderNo(orderNo)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
                    return registerInvoice(order, item.deliveryCompany(), item.trackingNumber(), actorId);
                });
                rows.add(new BulkInvoiceResponse.Row(i + 1, orderNo, true, result == null ? "등록되었습니다." : result.message()));
                success++;
            } catch (BusinessException ex) {
                rows.add(new BulkInvoiceResponse.Row(i + 1, orderNo, false, ex.getMessage()));
            } catch (DataAccessException ex) {
                rows.add(new BulkInvoiceResponse.Row(i + 1, orderNo, false, "다른 작업과 충돌했습니다. 다시 시도해 주세요."));
            }
        }
        auditLogService.record(actorId, "SHIPMENT_INVOICE_BULK", "SHIPMENT", "bulk",
                "total=" + items.size() + ", success=" + success);
        log.info("[SHIPPING] bulk invoice total={} success={} actorMemberId={}", items.size(), success, actorId);
        return new BulkInvoiceResponse(items.size(), success, items.size() - success, rows);
    }

    @Transactional
    public ShipmentActionResponse changeStatus(Long orderId, ShipmentStatus target, Long actorId) {
        OrderEntity order = requireOrder(orderId);
        requireActionable(order);
        if (!MANUAL_TARGETS.contains(target)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "관리자가 직접 변경할 수 없는 배송 상태입니다.");
        }
        Instant now = clock.instant();
        Shipment shipment = getOrCreateDelivery(order, now);
        ShipmentStatus current = shipment.getShipmentStatus();
        if (current == target) {
            return new ShipmentActionResponse(true, "이미 " + target.getLabel() + " 상태입니다.", assembler.toView(shipment, true));
        }
        if (!current.canAdvanceTo(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    current.getLabel() + " 상태에서 " + target.getLabel() + "(으)로 변경할 수 없습니다.");
        }
        if (target.getRank() >= ShipmentStatus.PICKED_UP.getRank() && !shipment.hasTrackingNumber()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "송장번호를 먼저 등록해 주세요.");
        }
        transition(shipment, order, target, now, "관리자가 '" + target.getLabel() + "' 상태로 변경했습니다.", actorId, false);
        auditLogService.record(actorId, "SHIPMENT_STATUS_UPDATE", "ORDER", order.getOrderNo(),
                "from=" + current + ", to=" + target);
        return new ShipmentActionResponse(true, target.getLabel() + " 상태로 변경했습니다.", assembler.toView(shipment, true));
    }

    @Transactional
    public ShipmentActionResponse requestPickup(Long orderId, String deliveryCompany, Long actorId) {
        OrderEntity order = requireOrder(orderId);
        requireActionable(order);
        Instant now = clock.instant();
        Shipment shipment = getOrCreateDelivery(order, now);
        if (!shipment.getShipmentStatus().canAdvanceTo(ShipmentStatus.PICKUP_REQUESTED)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    shipment.getShipmentStatus().getLabel() + " 상태에서는 수거요청을 할 수 없습니다.");
        }
        String companyCode = isBlank(deliveryCompany)
                ? shipment.getDeliveryCompany()
                : deliveryCompanyService.resolveEnabled(deliveryCompany).getCode();

        PickupResponse response = pickupService.requestPickup(new PickupRequest(
                order.getOrderId(), order.getOrderNo(), ShipmentType.DELIVERY, companyCode,
                null, null, null, null, null, order.getOrderMemo()));
        if (!response.accepted()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    response.message() == null ? "수거요청에 실패했습니다." : response.message());
        }
        shipment.setDeliveryCompany(companyCode);
        if (!isBlank(response.trackingNumber())) {
            shipment.setTrackingNumber(normalizeTrackingNumber(response.trackingNumber()));
        }
        transition(shipment, order, ShipmentStatus.PICKUP_REQUESTED, now, "택배 집하(수거)를 요청했습니다.", actorId, false);
        auditLogService.record(actorId, "SHIPMENT_PICKUP_REQUEST", "ORDER", order.getOrderNo(),
                "service=" + pickupService.name() + ", company=" + companyCode);
        return new ShipmentActionResponse(true, response.message(), assembler.toView(shipment, true));
    }

    @Transactional(readOnly = true)
    public WaybillResponse issueWaybill(Long orderId) {
        OrderEntity order = requireOrder(orderId);
        Shipment shipment = shipmentRepository.findByOrderIdAndShipmentType(orderId, ShipmentType.DELIVERY).orElse(null);
        return waybillService.issue(new WaybillRequest(order.getOrderId(), order.getOrderNo(),
                shipment == null ? null : shipment.getDeliveryCompany()));
    }

    @Transactional(readOnly = true)
    public WaybillResponse printWaybill(Long orderId) {
        OrderEntity order = requireOrder(orderId);
        Shipment shipment = shipmentRepository.findByOrderIdAndShipmentType(orderId, ShipmentType.DELIVERY).orElse(null);
        return waybillService.print(new WaybillRequest(order.getOrderId(), order.getOrderNo(),
                shipment == null ? null : shipment.getDeliveryCompany()));
    }

    public boolean waybillSupported() {
        return waybillService.isSupported();
    }

    public String pickupServiceName() {
        return pickupService.name();
    }

    @Transactional(readOnly = true)
    public ShipmentView findView(Long orderId, ShipmentType type) {
        return shipmentRepository.findByOrderIdAndShipmentType(orderId, type)
                .map(s -> assembler.toView(s, true))
                .orElse(null);
    }

    // ------------------------------------------------------------------ order status -> shipment

    /** Mirrors admin order-status edits (legacy status dropdown) onto the delivery shipment. */
    @Transactional
    public void syncFromOrderStatus(Long orderId, OrderStatus to, Long actorId) {
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        Instant now = clock.instant();
        switch (to) {
            case PREPARING -> getOrCreateDelivery(order, now);
            case SHIPPED -> advanceIfPossible(getOrCreateDelivery(order, now), order, ShipmentStatus.IN_TRANSIT, now,
                    "관리자가 주문을 배송중으로 변경했습니다.", actorId);
            case DELIVERED -> advanceIfPossible(getOrCreateDelivery(order, now), order, ShipmentStatus.DELIVERED, now,
                    "관리자가 주문을 배송완료로 변경했습니다.", actorId);
            case CANCELLED -> shipmentRepository.findByOrderIdAndShipmentType(orderId, ShipmentType.DELIVERY)
                    .ifPresent(s -> advanceIfPossible(s, order, ShipmentStatus.CANCELLED, now,
                            "주문이 취소되어 배송이 취소되었습니다.", actorId));
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ tracking sync

    /** Applies a status derived from courier tracking. Moves forward only; no-op otherwise. */
    void applyTrackingStatus(Shipment shipment, ShipmentStatus target, Instant at) {
        if (target == null || !shipment.getShipmentStatus().canAdvanceTo(target)) {
            return;
        }
        transition(shipment, null, target, at, null, null, true);
    }

    // ------------------------------------------------------------------ return shipments

    Shipment createReturnShipment(ReturnRequest request, Instant now) {
        Shipment shipment = new Shipment();
        shipment.setOrderId(request.getOrderId());
        shipment.setReturnRequestId(request.getReturnRequestId());
        shipment.setShipmentType(ShipmentType.RETURN);
        shipment.setShipmentStatus(ShipmentStatus.RETURN_REQUESTED);
        shipment.setCreatedAt(now);
        shipment.setUpdatedAt(now);
        Shipment saved = shipmentRepository.save(shipment);
        addInternalEvent(saved, now, "반품이 신청되었습니다.", ShipmentStatus.RETURN_REQUESTED);
        return saved;
    }

    void deleteReturnShipment(Long returnRequestId) {
        shipmentRepository.findByReturnRequestId(returnRequestId).ifPresent(shipmentRepository::delete);
    }

    /**
     * Updates the return shipment for an admin return action. Return-request statuses are managed by
     * the caller, so this does not write back to the return request.
     */
    Shipment updateReturnShipment(
            ReturnRequest request,
            ShipmentStatus target,
            String companyCode,
            String trackingNumber,
            Instant now,
            String description,
            Long actorId) {
        Shipment shipment = shipmentRepository.findByReturnRequestId(request.getReturnRequestId())
                .orElseGet(() -> createReturnShipment(request, now));
        if (companyCode != null && trackingNumber != null) {
            boolean changed = !companyCode.equals(shipment.getDeliveryCompany())
                    || !trackingNumber.equals(shipment.getTrackingNumber());
            if (changed) {
                if (shipment.hasTrackingNumber()) {
                    eventRepository.deleteByShipmentIdAndSource(shipment.getShipmentId(), TrackingEventSource.PROVIDER);
                }
                shipment.setDeliveryCompany(companyCode);
                shipment.setTrackingNumber(trackingNumber);
                shipment.setLastTrackingCheckedAt(null);
                shipment.setLastTrackingError(null);
                shipment.setTrackingFailCount(0);
            }
        }
        if (target != null && shipment.getShipmentStatus().canAdvanceTo(target)) {
            transition(shipment, null, target, now, description, actorId, false);
        } else {
            shipment.setUpdatedAt(now);
            shipmentRepository.save(shipment);
            if (description != null) {
                addInternalEvent(shipment, now, description, shipment.getShipmentStatus());
            }
        }
        return shipment;
    }

    // ------------------------------------------------------------------ shared helpers

    public static String normalizeTrackingNumber(String input) {
        String value = input == null ? "" : input.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
        if (!TRACKING_NUMBER.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "송장번호는 영문/숫자 6~30자로 입력해 주세요.");
        }
        return value;
    }

    private ShipmentActionResponse registerInvoice(OrderEntity order, String companyInput, String trackingInput, Long actorId) {
        requireActionable(order);
        DeliveryCompany company = deliveryCompanyService.resolveEnabled(companyInput);
        String trackingNumber = normalizeTrackingNumber(trackingInput);
        Instant now = clock.instant();

        Shipment shipment = getOrCreateDelivery(order, now);
        if (shipment.getShipmentStatus().isFinal()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "배송이 완료되었거나 취소된 건은 송장을 변경할 수 없습니다.");
        }
        boolean changed = !company.getCode().equals(shipment.getDeliveryCompany())
                || !trackingNumber.equals(shipment.getTrackingNumber());
        if (!changed) {
            return new ShipmentActionResponse(true, "이미 등록된 송장번호입니다.", assembler.toView(shipment, true));
        }
        boolean correction = shipment.hasTrackingNumber();
        if (correction) {
            eventRepository.deleteByShipmentIdAndSource(shipment.getShipmentId(), TrackingEventSource.PROVIDER);
        }
        shipment.setDeliveryCompany(company.getCode());
        shipment.setTrackingNumber(trackingNumber);
        shipment.setLastTrackingCheckedAt(null);
        shipment.setLastTrackingError(null);
        shipment.setTrackingFailCount(0);

        String description = (correction ? "송장번호가 변경되었습니다." : "상품이 발송되었습니다.")
                + " (" + company.getCompanyName() + ")";
        if (shipment.getShipmentStatus().canAdvanceTo(ShipmentStatus.IN_TRANSIT)) {
            transition(shipment, order, ShipmentStatus.IN_TRANSIT, now, description, actorId, false);
        } else {
            shipment.setUpdatedAt(now);
            shipmentRepository.save(shipment);
            addInternalEvent(shipment, now, description, shipment.getShipmentStatus());
        }

        auditLogService.record(actorId, "SHIPMENT_INVOICE_REGISTER", "ORDER", order.getOrderNo(),
                "company=" + company.getCode() + ", trackingNumber="
                        + ShippingLogMasker.maskTrackingNumber(trackingNumber) + (correction ? ", correction" : ""));
        log.info("[SHIPPING] invoice registered orderId={} company={} trackingNumber={} status={} actorMemberId={}",
                order.getOrderId(), company.getCode(), ShippingLogMasker.maskTrackingNumber(trackingNumber),
                shipment.getShipmentStatus(), actorId);
        return new ShipmentActionResponse(true,
                correction ? "송장번호가 수정되었습니다." : "송장번호가 등록되었습니다.",
                assembler.toView(shipment, true));
    }

    private void advanceIfPossible(
            Shipment shipment, OrderEntity order, ShipmentStatus target, Instant now, String description, Long actorId) {
        if (shipment.getShipmentStatus().canAdvanceTo(target)) {
            transition(shipment, order, target, now, description, actorId, false);
        }
    }

    private Shipment getOrCreateDelivery(OrderEntity order, Instant now) {
        return shipmentRepository.findByOrderIdAndShipmentType(order.getOrderId(), ShipmentType.DELIVERY)
                .orElseGet(() -> {
                    if (!SHIPMENT_ORDER_STATUSES.contains(order.getOrderStatus())) {
                        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "배송을 시작할 수 없는 주문 상태입니다.");
                    }
                    Shipment created = new Shipment();
                    created.setOrderId(order.getOrderId());
                    created.setShipmentType(ShipmentType.DELIVERY);
                    created.setShipmentStatus(ShipmentStatus.PREPARING);
                    created.setCreatedAt(now);
                    created.setUpdatedAt(now);
                    Shipment saved = shipmentRepository.save(created);
                    addInternalEvent(saved, now, "상품 준비를 시작했습니다.", ShipmentStatus.PREPARING);
                    syncOrderFromShipment(order, ShipmentStatus.PREPARING, now, null);
                    return saved;
                });
    }

    /**
     * Single place for status changes.
     *
     * @param order       the shipment's order, or null to load it
     * @param description internal timeline entry; null when the courier's own events describe the change
     * @param fromTracking true when the change comes from courier tracking (also updates the return request)
     */
    private void transition(
            Shipment shipment,
            OrderEntity order,
            ShipmentStatus to,
            Instant at,
            String description,
            Long actorId,
            boolean fromTracking) {
        ShipmentStatus from = shipment.getShipmentStatus();
        Instant now = clock.instant();
        shipment.setShipmentStatus(to);
        applyTimestamps(shipment, to, at);
        shipment.setUpdatedAt(now);
        shipmentRepository.save(shipment);
        if (description != null) {
            addInternalEvent(shipment, at, description, to);
        }

        OrderEntity target = order != null ? order : orderRepository.findById(shipment.getOrderId()).orElse(null);
        if (shipment.getShipmentType() == ShipmentType.DELIVERY && target != null) {
            syncOrderFromShipment(target, to, now, actorId);
        }
        if (shipment.getShipmentType() == ShipmentType.RETURN && fromTracking) {
            syncReturnFromTracking(shipment, to, at);
        }

        log.info("[SHIPPING] status orderId={} type={} company={} trackingNumber={} from={} to={} source={}",
                shipment.getOrderId(), shipment.getShipmentType(), shipment.getDeliveryCompany(),
                ShippingLogMasker.maskTrackingNumber(shipment.getTrackingNumber()), from, to,
                fromTracking ? "TRACKING" : actorId == null ? "SYSTEM" : "ADMIN");
        eventPublisher.publishEvent(new ShipmentStatusChangedEvent(
                shipment.getShipmentId(),
                shipment.getOrderId(),
                target == null ? null : target.getOrderNo(),
                target == null ? null : target.getMemberId(),
                shipment.getShipmentType(),
                from,
                to,
                shipment.getDeliveryCompany(),
                ShippingLogMasker.maskTrackingNumber(shipment.getTrackingNumber()),
                at));
    }

    private static void applyTimestamps(Shipment shipment, ShipmentStatus to, Instant at) {
        if ((to == ShipmentStatus.PICKUP_REQUESTED || to == ShipmentStatus.RETURN_PICKUP_REQUESTED)
                && shipment.getPickupRequestedAt() == null) {
            shipment.setPickupRequestedAt(at);
        }
        boolean moving = (to.belongsTo(ShipmentType.DELIVERY) && to.getRank() >= ShipmentStatus.PICKED_UP.getRank())
                || to == ShipmentStatus.RETURN_IN_TRANSIT || to == ShipmentStatus.RETURN_COMPLETED;
        if (moving) {
            if (shipment.getShippedAt() == null) {
                shipment.setShippedAt(at);
            }
            if (shipment.getPickedUpAt() == null) {
                shipment.setPickedUpAt(at);
            }
        }
        if (to == ShipmentStatus.DELIVERED || to == ShipmentStatus.RETURN_COMPLETED) {
            shipment.setDeliveredAt(at);
        }
    }

    private void syncOrderFromShipment(OrderEntity order, ShipmentStatus to, Instant now, Long actorId) {
        OrderStatus current = order.getOrderStatus();
        if (!ACTIONABLE_ORDER_STATUSES.contains(current)) {
            return;
        }
        OrderStatus next = switch (to) {
            case PREPARING, READY, PICKUP_REQUESTED -> OrderStatus.PREPARING;
            case PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY -> OrderStatus.SHIPPED;
            case DELIVERED -> OrderStatus.DELIVERED;
            default -> null;
        };
        if (next == null || next == current || (current == OrderStatus.SHIPPED && next == OrderStatus.PREPARING)) {
            return;
        }
        order.setOrderStatus(next);
        order.setUpdatedAt(now);
        orderRepository.save(order);
        auditLogService.record(actorId, "ORDER_STATUS_SYNC", "ORDER", order.getOrderNo(),
                "from=" + current + ", to=" + next + ", shipment=" + to);
    }

    private void syncReturnFromTracking(Shipment shipment, ShipmentStatus to, Instant at) {
        if (shipment.getReturnRequestId() == null) {
            return;
        }
        ReturnRequest request = returnRequestRepository.findById(shipment.getReturnRequestId()).orElse(null);
        if (request == null) {
            return;
        }
        ReturnStatus current = request.getReturnStatus();
        if (current == ReturnStatus.REFUNDED || current == ReturnStatus.REJECTED || current == ReturnStatus.RECEIVED) {
            return;
        }
        if (to == ShipmentStatus.RETURN_IN_TRANSIT && current != ReturnStatus.IN_TRANSIT) {
            request.setReturnStatus(ReturnStatus.IN_TRANSIT);
            if (request.getPickedUpAt() == null) {
                request.setPickedUpAt(at);
            }
        } else if (to == ShipmentStatus.RETURN_COMPLETED) {
            request.setReturnStatus(ReturnStatus.RECEIVED);
            request.setReceivedAt(at);
        } else {
            return;
        }
        request.setUpdatedAt(clock.instant());
        returnRequestRepository.save(request);
    }

    private void addInternalEvent(Shipment shipment, Instant at, String description, ShipmentStatus status) {
        ShipmentTrackingEvent event = new ShipmentTrackingEvent();
        event.setShipmentId(shipment.getShipmentId());
        event.setSource(TrackingEventSource.INTERNAL);
        event.setEventTime(at);
        event.setDescription(description);
        event.setStatus(status);
        event.setCreatedAt(clock.instant());
        eventRepository.save(event);
    }

    private OrderEntity requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    private static void requireActionable(OrderEntity order) {
        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.CANCEL_REQUESTED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "취소 요청 중인 주문입니다. 취소 요청을 먼저 처리해 주세요.");
        }
        if (!ACTIONABLE_ORDER_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "배송 처리를 할 수 없는 주문 상태입니다. (" + status + ")");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
