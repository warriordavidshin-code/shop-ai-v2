package com.petitcamel.shop.order.service;

import com.petitcamel.shop.admin.dto.AdminOrderSummaryResponse;
import com.petitcamel.shop.cart.repository.CartItemRepository;
import com.petitcamel.shop.cart.repository.CartRepository;
import com.petitcamel.shop.cart.service.CartService;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.domain.InventoryMovement;
import com.petitcamel.shop.inventory.domain.MovementType;
import com.petitcamel.shop.inventory.repository.InventoryMovementRepository;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderItem;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.dto.CreateOrderRequest;
import com.petitcamel.shop.order.dto.OrderItemRequest;
import com.petitcamel.shop.order.dto.OrderItemResponse;
import com.petitcamel.shop.order.dto.OrderResponse;
import com.petitcamel.shop.order.dto.OrderSummaryResponse;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.order.repository.OrderItemRepository;
import com.petitcamel.shop.payment.domain.Payment;
import com.petitcamel.shop.payment.domain.PaymentStatus;
import com.petitcamel.shop.payment.dto.PaymentResponse;
import com.petitcamel.shop.payment.gateway.PaymentApproveCommand;
import com.petitcamel.shop.payment.gateway.PaymentApproveResult;
import com.petitcamel.shop.payment.gateway.PaymentGateway;
import com.petitcamel.shop.payment.repository.PaymentRepository;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderEntityRepository orderEntityRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentGateway paymentGateway;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public OrderService(
            OrderEntityRepository orderEntityRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductSkuRepository productSkuRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository inventoryMovementRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            PaymentGateway paymentGateway,
            AuditLogService auditLogService,
            Clock clock) {
        this.orderEntityRepository = orderEntityRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productSkuRepository = productSkuRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.paymentGateway = paymentGateway;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse createOrder(Long memberId, String idempotencyKey, CreateOrderRequest request) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 헤더가 필요합니다.");
        }

        return orderEntityRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.info("Idempotent order reuse memberId={} orderNo={}", memberId, existing.getOrderNo());
                    return toOrderResponse(existing, requireOwner(existing, memberId));
                })
                .orElseGet(() -> createNewOrder(memberId, idempotencyKey.trim(), request));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long memberId, String orderNo) {
        OrderEntity order = requireOrderByNo(orderNo);
        requireOwner(order, memberId);
        return toOrderResponse(order, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMyOrders(Long memberId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<OrderEntity> orders = orderEntityRepository.findByMemberIdOrderByOrderedAtDesc(memberId, pageable);

        List<Long> orderIds = orders.getContent().stream().map(OrderEntity::getOrderId).toList();
        Map<Long, Long> itemCountByOrder = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));

        List<OrderSummaryResponse> content = orders.getContent().stream()
                .map(order -> new OrderSummaryResponse(
                        order.getOrderId(),
                        order.getOrderNo(),
                        order.getOrderStatus(),
                        order.getPaymentAmount(),
                        order.getOrderedAt(),
                        itemCountByOrder.getOrDefault(order.getOrderId(), 0L).intValue()))
                .toList();

        return PageResponse.of(content, orders.getNumber(), orders.getSize(), orders.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> listAdminOrders(int page, int size, OrderStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize);

        Page<OrderEntity> orders = status == null
                ? orderEntityRepository.findAllByOrderByOrderedAtDesc(pageable)
                : orderEntityRepository.findByOrderStatusOrderByOrderedAtDesc(status, pageable);

        List<Long> orderIds = orders.getContent().stream().map(OrderEntity::getOrderId).toList();
        Map<Long, Long> itemCountByOrder = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));

        List<AdminOrderSummaryResponse> content = orders.getContent().stream()
                .map(order -> new AdminOrderSummaryResponse(
                        order.getOrderId(),
                        order.getOrderNo(),
                        order.getMemberId(),
                        order.getOrderStatus(),
                        order.getPaymentAmount(),
                        order.getOrderedAt(),
                        itemCountByOrder.getOrDefault(order.getOrderId(), 0L).intValue()))
                .toList();

        return PageResponse.of(content, orders.getNumber(), orders.getSize(), orders.getTotalElements());
    }

    @Transactional
    public OrderResponse updateAdminOrderStatus(String orderNo, OrderStatus newStatus, Long actorMemberId) {
        OrderEntity order = requireOrderByNo(orderNo);
        OrderStatus previous = order.getOrderStatus();
        if (previous == newStatus) {
            return toOrderResponse(order, true);
        }

        Instant now = clock.instant();
        order.setOrderStatus(newStatus);
        order.setUpdatedAt(now);
        orderEntityRepository.save(order);

        auditLogService.record(
                actorMemberId,
                "ORDER_STATUS_UPDATE",
                "ORDER",
                order.getOrderNo(),
                "from=" + previous + ", to=" + newStatus);

        return toOrderResponse(order, true);
    }

    @Transactional
    public PaymentResponse approveMockPayment(Long memberId, String orderNo) {
        OrderEntity order = requireOrderByNo(orderNo);
        requireOwner(order, memberId);

        if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "결제 대기 상태의 주문만 승인할 수 있습니다.");
        }

        Payment payment = paymentRepository.findByOrderId(order.getOrderId()).stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.READY)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        PaymentApproveResult result = paymentGateway.approve(new PaymentApproveCommand(
                order.getOrderNo(),
                payment.getPaymentId(),
                payment.getPaymentAmount(),
                payment.getPaymentMethod()));

        if (!result.approved()) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    result.message() == null ? "결제 승인에 실패했습니다." : result.message());
        }

        Instant now = clock.instant();
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
        for (OrderItem item : items) {
            confirmSaleInventory(item.getSkuId(), item.getQuantity(), order.getOrderNo(), memberId, now);
        }

        payment.setPaymentStatus(PaymentStatus.APPROVED);
        payment.setProviderTransactionId(result.providerTransactionId());
        payment.setApprovedAt(now);
        paymentRepository.save(payment);

        order.setOrderStatus(OrderStatus.PAID);
        order.setUpdatedAt(now);
        orderEntityRepository.save(order);
        log.info("Mock payment approved memberId={} orderNo={} amount={}",
                memberId, order.getOrderNo(), payment.getPaymentAmount());

        return new PaymentResponse(
                payment.getPaymentId(),
                order.getOrderNo(),
                order.getOrderStatus(),
                payment.getPaymentStatus(),
                payment.getPaymentAmount(),
                payment.getPaymentMethod(),
                payment.getProviderTransactionId(),
                payment.getApprovedAt());
    }

    @Transactional
    public OrderResponse cancelOrder(Long memberId, String orderNo) {
        OrderEntity order = requireOrderByNo(orderNo);
        requireOwner(order, memberId);

        OrderStatus status = order.getOrderStatus();
        if (status != OrderStatus.PAYMENT_PENDING && status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "취소할 수 없는 주문 상태입니다.");
        }

        Instant now = clock.instant();
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());

        for (OrderItem item : items) {
            if (status == OrderStatus.PAYMENT_PENDING) {
                releaseReservation(item.getSkuId(), item.getQuantity(), order.getOrderNo(), memberId, now);
            } else {
                restoreAfterPaidCancel(item.getSkuId(), item.getQuantity(), order.getOrderNo(), memberId, now);
            }
            item.setStatus("CANCELLED");
        }
        orderItemRepository.saveAll(items);

        for (Payment payment : paymentRepository.findByOrderId(order.getOrderId())) {
            if (payment.getPaymentStatus() != PaymentStatus.CANCELLED) {
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                payment.setCancelledAt(now);
                paymentRepository.save(payment);
            }
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(now);
        orderEntityRepository.save(order);
        log.info("Order cancelled memberId={} orderNo={} previousStatus={}", memberId, orderNo, status);

        return toOrderResponse(order, true);
    }

    private OrderResponse createNewOrder(Long memberId, String idempotencyKey, CreateOrderRequest request) {
        Map<Long, Integer> quantityBySku = mergeQuantities(request.items());
        List<Long> skuIds = List.copyOf(quantityBySku.keySet());

        Map<Long, ProductSku> skus = productSkuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getSkuId, Function.identity()));
        if (skus.size() != skuIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU를 찾을 수 없습니다.");
        }

        List<Long> productIds = skus.values().stream().map(ProductSku::getProductId).distinct().toList();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));

        Instant now = clock.instant();
        List<PreparedLine> lines = new ArrayList<>();
        BigDecimal productAmount = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : quantityBySku.entrySet()) {
            Long skuId = entry.getKey();
            int quantity = entry.getValue();
            ProductSku sku = skus.get(skuId);
            if (sku.getStatus() != ProductStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "판매 중인 SKU만 주문할 수 있습니다.");
            }
            Product product = products.get(sku.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
            }
            if (product.getStatus() != ProductStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "판매 중인 상품만 주문할 수 있습니다.");
            }

            BigDecimal unitPrice = product.getSalePrice().add(
                    sku.getAdditionalPrice() == null ? BigDecimal.ZERO : sku.getAdditionalPrice());
            BigDecimal linePayment = unitPrice.multiply(BigDecimal.valueOf(quantity));
            productAmount = productAmount.add(linePayment);

            lines.add(new PreparedLine(
                    product.getProductId(),
                    skuId,
                    product.getProductName(),
                    sku.getColor() + "/" + sku.getSize(),
                    quantity,
                    unitPrice,
                    linePayment));
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal deliveryAmount = productAmount.compareTo(BigDecimal.ZERO) > 0
                ? CartService.DELIVERY_FEE
                : BigDecimal.ZERO;
        BigDecimal paymentAmount = productAmount.subtract(discountAmount).add(deliveryAmount);

        for (PreparedLine line : lines) {
            reserveInventory(line.skuId(), line.quantity(), memberId, now);
        }

        String orderNo = generateUniqueOrderNo();
        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderNo);
        order.setMemberId(memberId);
        order.setOrderStatus(OrderStatus.PAYMENT_PENDING);
        order.setTotalProductAmount(productAmount);
        order.setDiscountAmount(discountAmount);
        order.setDeliveryAmount(deliveryAmount);
        order.setPaymentAmount(paymentAmount);
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setPostcode(request.postcode());
        order.setAddress1(request.address1());
        order.setAddress2(request.address2());
        order.setOrderMemo(request.orderMemo());
        order.setIdempotencyKey(idempotencyKey);
        order.setOrderedAt(now);
        order.setUpdatedAt(now);

        try {
            order = orderEntityRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            return orderEntityRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> toOrderResponse(existing, requireOwner(existing, memberId)))
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "주문을 생성할 수 없습니다."));
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (PreparedLine line : lines) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getOrderId());
            item.setProductId(line.productId());
            item.setSkuId(line.skuId());
            item.setProductName(line.productName());
            item.setOptionName(line.optionName());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setDiscountPrice(BigDecimal.ZERO);
            item.setPaymentPrice(line.paymentPrice());
            item.setStatus("ORDERED");
            item.setCreatedAt(now);
            orderItems.add(item);

            writeMovement(line.skuId(), MovementType.RESERVATION, line.quantity(),
                    "ORDER", orderNo, "주문 재고 예약", memberId, now);
        }
        orderItemRepository.saveAll(orderItems);

        Payment payment = new Payment();
        payment.setOrderId(order.getOrderId());
        payment.setPaymentMethod("MOCK");
        payment.setProvider("MOCK");
        payment.setPaymentAmount(paymentAmount);
        payment.setPaymentStatus(PaymentStatus.READY);
        payment.setCreatedAt(now);
        paymentRepository.save(payment);

        clearOrderedCartItems(memberId, skuIds, now);
        log.info("Order created memberId={} orderNo={} paymentAmount={} itemCount={}",
                memberId, order.getOrderNo(), paymentAmount, lines.size());

        return toOrderResponse(order, true);
    }

    private void clearOrderedCartItems(Long memberId, List<Long> skuIds, Instant now) {
        cartRepository.findByMemberId(memberId).ifPresent(cart -> {
            cartItemRepository.deleteByCartIdAndSkuIdIn(cart.getCartId(), skuIds);
            cart.setUpdatedAt(now);
            cartRepository.save(cart);
        });
    }

    private void reserveInventory(Long skuId, int quantity, Long memberId, Instant now) {
        Inventory inventory = inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        try {
            inventory.reserve(quantity);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "재고가 부족합니다. (가용 수량: " + inventory.getAvailableQuantity() + ")");
        }
        inventory.setUpdatedAt(now);
        saveInventory(inventory);
    }

    private void releaseReservation(Long skuId, int quantity, String orderNo, Long memberId, Instant now) {
        Inventory inventory = inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        try {
            inventory.release(quantity);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage());
        }
        inventory.setUpdatedAt(now);
        saveInventory(inventory);
        writeMovement(skuId, MovementType.CANCEL, quantity, "ORDER", orderNo, "주문 취소(예약 해제)", memberId, now);
    }

    private void confirmSaleInventory(Long skuId, int quantity, String orderNo, Long memberId, Instant now) {
        Inventory inventory = inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        try {
            inventory.confirmSale(quantity);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage());
        }
        inventory.setUpdatedAt(now);
        saveInventory(inventory);
        writeMovement(skuId, MovementType.SALE, quantity, "ORDER", orderNo, "결제 승인 재고 차감", memberId, now);
    }

    private void restoreAfterPaidCancel(Long skuId, int quantity, String orderNo, Long memberId, Instant now) {
        Inventory inventory = inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        try {
            inventory.restoreStock(quantity);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage());
        }
        inventory.setUpdatedAt(now);
        saveInventory(inventory);
        writeMovement(skuId, MovementType.CANCEL, quantity, "ORDER", orderNo, "결제 후 주문 취소(재고 복구)", memberId, now);
    }

    private void saveInventory(Inventory inventory) {
        try {
            inventoryRepository.saveAndFlush(inventory);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "재고가 다른 요청에 의해 변경되었습니다. 다시 시도해 주세요.");
        }
    }

    private void writeMovement(
            Long skuId,
            MovementType type,
            int quantity,
            String referenceType,
            String referenceId,
            String reason,
            Long actorMemberId,
            Instant now) {
        InventoryMovement movement = new InventoryMovement();
        movement.setSkuId(skuId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setReason(reason);
        movement.setActorMemberId(actorMemberId);
        movement.setCreatedAt(now);
        inventoryMovementRepository.save(movement);
    }

    private String generateUniqueOrderNo() {
        for (int i = 0; i < 5; i++) {
            String candidate = OrderNoGenerator.generate(clock);
            if (orderEntityRepository.findByOrderNo(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "주문번호 생성에 실패했습니다.");
    }

    private Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            if (item == null || item.skuId() == null || item.quantity() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "주문 항목이 올바르지 않습니다.");
            }
            merged.merge(item.skuId(), item.quantity(), Integer::sum);
        }
        return merged;
    }

    private OrderEntity requireOrderByNo(String orderNo) {
        return orderEntityRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    private boolean requireOwner(OrderEntity order, Long memberId) {
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 주문만 조회할 수 있습니다.");
        }
        return true;
    }

    private OrderResponse toOrderResponse(OrderEntity order, boolean ignored) {
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getOrderId()).stream()
                .map(item -> new OrderItemResponse(
                        item.getOrderItemId(),
                        item.getProductId(),
                        item.getSkuId(),
                        item.getProductName(),
                        item.getOptionName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getDiscountPrice(),
                        item.getPaymentPrice(),
                        item.getStatus()))
                .toList();

        Payment payment = paymentRepository.findByOrderId(order.getOrderId()).stream()
                .findFirst()
                .orElse(null);

        return new OrderResponse(
                order.getOrderId(),
                order.getOrderNo(),
                order.getOrderStatus(),
                order.getTotalProductAmount(),
                order.getDiscountAmount(),
                order.getDeliveryAmount(),
                order.getPaymentAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getPostcode(),
                order.getAddress1(),
                order.getAddress2(),
                order.getOrderMemo(),
                order.getOrderedAt(),
                items,
                payment == null ? null : payment.getPaymentStatus(),
                payment == null ? null : payment.getPaymentMethod());
    }

    private record PreparedLine(
            Long productId,
            Long skuId,
            String productName,
            String optionName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal paymentPrice
    ) {
    }
}
