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
import com.petitcamel.shop.shipping.provider.ProviderActionResult;
import com.petitcamel.shop.shipping.provider.ShipmentCommand;
import com.petitcamel.shop.shipping.repository.ReturnRequestRepository;
import com.petitcamel.shop.shipping.repository.ShipmentRepository;
import com.petitcamel.shop.shipping.repository.ShipmentTrackingEventRepository;
import com.petitcamel.shop.shipping.support.ShippingLogMasker;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shipment lifecycle: invoice registration, status transitions and keeping the order status in step.
 * All status changes go through {@link #transition} so timestamps, events, order sync and
 * notifications stay consistent regardless of whether an admin, a vendor or the tracking sync triggered them.
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

    /** Delivery statuses an admin may set by hand (waybill / pickup have their own actions). */
    static final Set<ShipmentStatus> MANUAL_TARGETS = EnumSet.of(
            ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY,
            ShipmentStatus.DELIVERED, ShipmentStatus.FAILED);

    private static final Set<ReturnStatus> RETURN_TRACKING_SYNCABLE =
            EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.PICKUP_REQUESTED, ReturnStatus.IN_PROGRESS);

    private static final Pattern TRACKING_NUMBER = Pattern.compile("^[A-Z0-9]{6,30}$");

    /**
     * Everything an external call needs about the shipment, read in one transaction before the vendor is called
     * (the call itself runs outside any transaction).
     */
    public record ExternalTarget(
            Long orderId,
            String orderNo,
            Long memberId,
            Long shipmentId,
            ShipmentType shipmentType,
            Long returnRequestId,
            Long deliveryCompanyId,
            String deliveryCompanyCode,
            ShipmentCommand.Contact contact,
            String memo,
            boolean alreadyDone
    ) {
    }

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository eventRepository;
    private final OrderEntityRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final DeliveryCompanyService deliveryCompanyService;
    private final ShippingOperationService operationService;
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
            ShippingOperationService operationService,
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
        this.operationService = operationService;
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
        ShipmentStatus current = shipment.getStatus();
        String targetLabel = target.labelFor(ShipmentType.DELIVERY);
        if (current == target) {
            return new ShipmentActionResponse(true, "이미 " + targetLabel + " 상태입니다.", assembler.toView(shipment, true));
        }
        if (!current.canAdvanceTo(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    current.labelFor(ShipmentType.DELIVERY) + " 상태에서 " + targetLabel + "(으)로 변경할 수 없습니다.");
        }
        if (target.getRank() >= ShipmentStatus.PICKED_UP.getRank() && !shipment.hasTrackingNumber()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "송장번호를 먼저 등록해 주세요.");
        }
        transition(shipment, order, target, now, "관리자가 '" + targetLabel + "' 상태로 변경했습니다.", actorId, false);
        auditLogService.record(actorId, "SHIPMENT_STATUS_UPDATE", "ORDER", order.getOrderNo(),
                "from=" + current + ", to=" + target);
        return new ShipmentActionResponse(true, targetLabel + " 상태로 변경했습니다.", assembler.toView(shipment, true));
    }

    @Transactional(readOnly = true)
    public ShipmentView findView(Long orderId, ShipmentType type) {
        return shipmentRepository.findFirstByOrderIdAndShipmentTypeOrderByShipmentIdDesc(orderId, type)
                .map(s -> assembler.toView(s, true))
                .orElse(null);
    }

    // ------------------------------------------------------------------ external actions (delivery)

    /**
     * Validates that the delivery shipment can move to {@code target} and collects what the vendor needs.
     * Creates the delivery shipment on first use.
     *
     * @param companyInput courier to use; blank keeps the courier already on the shipment (may stay null)
     */
    @Transactional
    public ExternalTarget prepareDeliveryAction(Long orderId, String companyInput, ShipmentStatus target) {
        OrderEntity order = requireOrder(orderId);
        requireActionable(order);
        Shipment shipment = getOrCreateDelivery(order, clock.instant());
        ShipmentStatus current = shipment.getStatus();
        boolean alreadyDone = current == target;
        if (!alreadyDone && !current.canAdvanceTo(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    current.labelFor(ShipmentType.DELIVERY) + " 상태에서는 "
                            + target.labelFor(ShipmentType.DELIVERY) + " 처리를 할 수 없습니다.");
        }
        Long companyId = shipment.getDeliveryCompanyId();
        String companyCode = null;
        if (!isBlank(companyInput)) {
            DeliveryCompany company = deliveryCompanyService.resolveEnabled(companyInput);
            companyId = company.getDeliveryCompanyId();
            companyCode = company.getCode();
        } else if (companyId != null) {
            companyCode = DeliveryCompanyService.companyCode(deliveryCompanyService.companiesById(), companyId);
        }
        return new ExternalTarget(
                order.getOrderId(),
                order.getOrderNo(),
                order.getMemberId(),
                shipment.getShipmentId(),
                ShipmentType.DELIVERY,
                null,
                companyId,
                companyCode,
                new ShipmentCommand.Contact(order.getReceiverName(), order.getReceiverPhone(),
                        order.getPostcode(), order.getAddress1(), order.getAddress2()),
                order.getOrderMemo(),
                alreadyDone);
    }

    /**
     * Saves a vendor result on the delivery shipment and marks the API operation applied in the same transaction.
     */
    @Transactional
    public ShipmentActionResponse applyDeliveryResult(
            ExternalTarget target,
            Long operationId,
            Long providerId,
            ProviderActionResult result,
            ShipmentStatus to,
            String description,
            Long actorId) {
        Shipment shipment = shipmentRepository.findById(target.shipmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
        OrderEntity order = requireOrder(target.orderId());
        applyResult(shipment, order, target.deliveryCompanyId(), providerId, result, to, description, actorId);
        operationService.markApplied(operationId);
        String message = result.message() != null ? result.message()
                : to.labelFor(ShipmentType.DELIVERY) + " 처리되었습니다.";
        return new ShipmentActionResponse(true, message, assembler.toView(shipment, true));
    }

    /** Current delivery view for a repeated click on an action that already went through. */
    @Transactional(readOnly = true)
    public ShipmentActionResponse alreadyDone(Long shipmentId, String message) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElse(null);
        return new ShipmentActionResponse(true, message, assembler.toView(shipment, true));
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
            case CANCELLED -> shipmentRepository
                    .findFirstByOrderIdAndShipmentTypeOrderByShipmentIdDesc(orderId, ShipmentType.DELIVERY)
                    .ifPresent(s -> advanceIfPossible(s, order, ShipmentStatus.CANCELLED, now,
                            "주문이 취소되어 배송이 취소되었습니다.", actorId));
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ tracking sync

    /** Applies a status derived from courier tracking. Moves forward only; no-op otherwise. */
    void applyTrackingStatus(Shipment shipment, ShipmentStatus target, Instant at) {
        if (target == null || target == ShipmentStatus.CANCELLED || !shipment.getStatus().canAdvanceTo(target)) {
            return;
        }
        transition(shipment, null, target, at, null, null, true);
    }

    // ------------------------------------------------------------------ return shipments

    Shipment createReturnShipment(ReturnRequest request, ShipmentCommand.Contact contact, Instant now) {
        Shipment shipment = new Shipment();
        shipment.setOrderId(request.getOrderId());
        shipment.setReturnRequestId(request.getReturnRequestId());
        shipment.setShipmentType(ShipmentType.RETURN);
        shipment.setStatus(ShipmentStatus.READY);
        shipment.setContact(contact.name(), contact.phone(), contact.postalCode(), contact.address1(), contact.address2());
        Shipment saved = shipmentRepository.save(shipment);
        eventRepository.save(ShipmentTrackingEvent.internal(
                saved.getShipmentId(), now, "반품이 접수되었습니다.", ShipmentStatus.READY, null));
        return saved;
    }

    /**
     * Updates a return shipment for an admin / vendor return action. The return request's own status is managed by
     * the caller, so this does not write back to it.
     */
    void updateReturnShipment(
            Shipment shipment,
            ShipmentStatus target,
            Long companyId,
            String trackingNumber,
            Long providerId,
            Instant now,
            String description,
            Long actorId) {
        if (companyId != null && trackingNumber != null) {
            assignInvoice(shipment, companyId, trackingNumber);
        }
        if (providerId != null) {
            shipment.setShippingProviderId(providerId);
        }
        if (target != null && shipment.getStatus().canAdvanceTo(target)) {
            transition(shipment, null, target, now, description, actorId, false);
        } else {
            shipmentRepository.save(shipment);
            if (description != null) {
                addInternalEvent(shipment, now, description, shipment.getStatus(), actorId);
            }
        }
    }

    /** Cancels a return shipment that has not been picked up (reject / customer withdrawal). */
    void cancelReturnShipment(Shipment shipment, Instant now, String description, Long actorId) {
        if (shipment != null && shipment.getStatus().canAdvanceTo(ShipmentStatus.CANCELLED)) {
            transition(shipment, null, ShipmentStatus.CANCELLED, now, description, actorId, false);
        }
    }

    /** Vendor result on any shipment: invoice (if issued), vendor, and the status move. */
    void applyResult(
            Shipment shipment,
            OrderEntity order,
            Long companyId,
            Long providerId,
            ProviderActionResult result,
            ShipmentStatus to,
            String description,
            Long actorId) {
        Instant now = clock.instant();
        if (!isBlank(result.trackingNumber())) {
            if (companyId == null) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "업체가 송장번호를 발급했지만 택배사 정보가 없어 저장할 수 없습니다. 택배사를 선택한 뒤 같은 버튼을 다시 눌러 주세요.");
            }
            assignInvoice(shipment, companyId, result.trackingNumber());
        }
        if (providerId != null) {
            shipment.setShippingProviderId(providerId);
        }
        if (shipment.getStatus().canAdvanceTo(to)) {
            transition(shipment, order, to, now, description, actorId, false);
        } else {
            shipmentRepository.save(shipment);
            if (description != null) {
                addInternalEvent(shipment, now, description, shipment.getStatus(), actorId);
            }
        }
    }

    // ------------------------------------------------------------------ shared helpers

    public static String normalizeTrackingNumber(String input) {
        String value = input == null ? "" : input.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
        if (!TRACKING_NUMBER.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "송장번호는 영문/숫자 6~30자로 입력해 주세요.");
        }
        return value;
    }

    /**
     * Points the shipment at an invoice; provider events of a replaced invoice are dropped.
     *
     * @return true when the invoice changed
     */
    boolean assignInvoice(Shipment shipment, Long companyId, String trackingInput) {
        String trackingNumber = normalizeTrackingNumber(trackingInput);
        boolean changed = !companyId.equals(shipment.getDeliveryCompanyId())
                || !trackingNumber.equals(shipment.getTrackingNumber());
        if (!changed) {
            return false;
        }
        shipmentRepository.findFirstByDeliveryCompanyIdAndTrackingNumberAndStatusNot(
                        companyId, trackingNumber, ShipmentStatus.CANCELLED)
                .filter(other -> !other.getShipmentId().equals(shipment.getShipmentId()))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "이미 다른 배송에 등록된 송장번호입니다.");
                });
        if (shipment.hasTrackingNumber() && shipment.getShipmentId() != null) {
            eventRepository.deleteByShipmentIdAndSource(shipment.getShipmentId(), TrackingEventSource.PROVIDER);
        }
        shipment.assignInvoice(companyId, trackingNumber);
        return true;
    }

    private ShipmentActionResponse registerInvoice(OrderEntity order, String companyInput, String trackingInput, Long actorId) {
        requireActionable(order);
        DeliveryCompany company = deliveryCompanyService.resolveEnabled(companyInput);
        Instant now = clock.instant();

        Shipment shipment = getOrCreateDelivery(order, now);
        if (shipment.getStatus().isFinal()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "배송이 완료되었거나 취소된 건은 송장을 변경할 수 없습니다.");
        }
        boolean correction = shipment.hasTrackingNumber();
        if (!assignInvoice(shipment, company.getDeliveryCompanyId(), trackingInput)) {
            return new ShipmentActionResponse(true, "이미 등록된 송장번호입니다.", assembler.toView(shipment, true));
        }
        String trackingNumber = shipment.getTrackingNumber();
        String description = (correction ? "송장번호가 변경되었습니다." : "상품이 발송되었습니다.")
                + " (" + company.getName() + ")";
        if (shipment.getStatus().canAdvanceTo(ShipmentStatus.IN_TRANSIT)) {
            transition(shipment, order, ShipmentStatus.IN_TRANSIT, now, description, actorId, false);
        } else {
            shipmentRepository.save(shipment);
            addInternalEvent(shipment, now, description, shipment.getStatus(), actorId);
        }

        auditLogService.record(actorId, "SHIPMENT_INVOICE_REGISTER", "ORDER", order.getOrderNo(),
                "company=" + company.getCode() + ", trackingNumber="
                        + ShippingLogMasker.maskTrackingNumber(trackingNumber) + (correction ? ", correction" : ""));
        log.info("[SHIPPING] invoice registered orderId={} company={} trackingNumber={} status={} actorMemberId={}",
                order.getOrderId(), company.getCode(), ShippingLogMasker.maskTrackingNumber(trackingNumber),
                shipment.getStatus(), actorId);
        return new ShipmentActionResponse(true,
                correction ? "송장번호가 수정되었습니다." : "송장번호가 등록되었습니다.",
                assembler.toView(shipment, true));
    }

    private void advanceIfPossible(
            Shipment shipment, OrderEntity order, ShipmentStatus target, Instant now, String description, Long actorId) {
        if (shipment.getStatus().canAdvanceTo(target)) {
            transition(shipment, order, target, now, description, actorId, false);
        }
    }

    private Shipment getOrCreateDelivery(OrderEntity order, Instant now) {
        return shipmentRepository.findFirstByOrderIdAndShipmentTypeOrderByShipmentIdDesc(order.getOrderId(), ShipmentType.DELIVERY)
                .orElseGet(() -> {
                    if (!SHIPMENT_ORDER_STATUSES.contains(order.getOrderStatus())) {
                        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "배송을 시작할 수 없는 주문 상태입니다.");
                    }
                    Shipment created = new Shipment();
                    created.setOrderId(order.getOrderId());
                    created.setShipmentType(ShipmentType.DELIVERY);
                    created.setStatus(ShipmentStatus.READY);
                    Shipment saved = shipmentRepository.saveAndFlush(created);
                    addInternalEvent(saved, now, "상품 준비를 시작했습니다.", ShipmentStatus.READY, null);
                    syncOrderFromShipment(order, ShipmentStatus.READY, now, null);
                    return saved;
                });
    }

    /**
     * Single place for status changes.
     *
     * @param order        the shipment's order, or null to load it
     * @param description  internal timeline entry; null when the courier's own events describe the change
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
        ShipmentStatus from = shipment.getStatus();
        Instant now = clock.instant();
        shipment.changeStatus(to, at);
        shipmentRepository.save(shipment);
        if (description != null) {
            addInternalEvent(shipment, at, description, to, actorId);
        }

        OrderEntity target = order != null ? order : orderRepository.findById(shipment.getOrderId()).orElse(null);
        if (shipment.getShipmentType() == ShipmentType.DELIVERY && target != null) {
            syncOrderFromShipment(target, to, now, actorId);
        }
        if (shipment.getShipmentType() == ShipmentType.RETURN && fromTracking) {
            syncReturnFromTracking(shipment, to, at);
        }

        String companyCode = DeliveryCompanyService.companyCode(companies(shipment), shipment.getDeliveryCompanyId());
        log.info("[SHIPPING] status orderId={} type={} company={} trackingNumber={} from={} to={} source={}",
                shipment.getOrderId(), shipment.getShipmentType(), companyCode,
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
                companyCode,
                ShippingLogMasker.maskTrackingNumber(shipment.getTrackingNumber()),
                at));
    }

    private Map<Long, DeliveryCompany> companies(Shipment shipment) {
        return shipment.getDeliveryCompanyId() == null ? Map.of() : deliveryCompanyService.companiesById();
    }

    private void syncOrderFromShipment(OrderEntity order, ShipmentStatus to, Instant now, Long actorId) {
        OrderStatus current = order.getOrderStatus();
        if (!ACTIONABLE_ORDER_STATUSES.contains(current)) {
            return;
        }
        OrderStatus next = switch (to) {
            case READY, WAYBILL_ISSUED, PICKUP_REQUESTED -> OrderStatus.PREPARING;
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

    /** Courier progress on a return parcel moves the return request forward (never backward). */
    private void syncReturnFromTracking(Shipment shipment, ShipmentStatus to, Instant at) {
        if (shipment.getReturnRequestId() == null) {
            return;
        }
        ReturnRequest request = returnRequestRepository.findById(shipment.getReturnRequestId()).orElse(null);
        if (request == null || !RETURN_TRACKING_SYNCABLE.contains(request.getStatus())) {
            return;
        }
        if (to == ShipmentStatus.DELIVERED) {
            request.setStatus(ReturnStatus.RECEIVED);
            request.setReceivedAt(at);
        } else if (to.isMoving() && request.getStatus() != ReturnStatus.IN_PROGRESS) {
            request.setStatus(ReturnStatus.IN_PROGRESS);
        } else {
            return;
        }
        returnRequestRepository.save(request);
    }

    private void addInternalEvent(Shipment shipment, Instant at, String description, ShipmentStatus status, Long actorId) {
        eventRepository.save(ShipmentTrackingEvent.internal(shipment.getShipmentId(), at, description, status, actorId));
    }

    private OrderEntity requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    static void requireActionable(OrderEntity order) {
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
