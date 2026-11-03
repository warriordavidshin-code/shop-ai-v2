package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.cost.service.ProductCostService;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.domain.MovementType;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.inventory.service.InventoryMovementWriter;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.AllocationMethod;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.MatchStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseLedger;
import com.petitcamel.shop.purchase.domain.PurchaseLedgerItem;
import com.petitcamel.shop.purchase.domain.PurchaseReceipt;
import com.petitcamel.shop.purchase.domain.PurchaseReceiptItem;
import com.petitcamel.shop.purchase.dto.ReceivingDtos;
import com.petitcamel.shop.purchase.repository.PurchaseLedgerItemRepository;
import com.petitcamel.shop.purchase.repository.PurchaseLedgerRepository;
import com.petitcamel.shop.purchase.repository.PurchaseReceiptItemRepository;
import com.petitcamel.shop.purchase.repository.PurchaseReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 입고관리. Registering a purchase never changes stock; only {@link #confirm} does, once per receipt:
 * the PENDING → CONFIRMED transition is claimed with a conditional update, so repeated or concurrent confirms
 * return the existing result without adding stock again. A unique index on the RECEIPT movement per receipt
 * line is the database backstop. Partial receiving leaves the remainder open on the ledger line.
 */
@Service
public class ReceivingService {

    private static final Logger log = LoggerFactory.getLogger(ReceivingService.class);
    private static final String REF_TYPE = "PURCHASE_RECEIPT_ITEM";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseReceiptItemRepository receiptItemRepository;
    private final PurchaseLedgerRepository ledgerRepository;
    private final PurchaseLedgerItemRepository ledgerItemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementWriter movementWriter;
    private final ProductCostService costService;
    private final ProductRepository productRepository;
    private final ProductSkuRepository skuRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ReceivingService(
            PurchaseReceiptRepository receiptRepository,
            PurchaseReceiptItemRepository receiptItemRepository,
            PurchaseLedgerRepository ledgerRepository,
            PurchaseLedgerItemRepository ledgerItemRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementWriter movementWriter,
            ProductCostService costService,
            ProductRepository productRepository,
            ProductSkuRepository skuRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.ledgerRepository = ledgerRepository;
        this.ledgerItemRepository = ledgerItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementWriter = movementWriter;
        this.costService = costService;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public ReceivingDtos.ReceiptResponse create(ReceivingDtos.CreateReceiptRequest request, Long actorId) {
        PurchaseLedger ledger = ledgerRepository.findByIdAndDeletedAtIsNull(request.purchaseLedgerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매입내역을 찾을 수 없습니다."));
        Map<Long, PurchaseLedgerItem> ledgerItems = ledgerItemRepository
                .findByPurchaseLedgerIdOrderByLineNoAsc(ledger.getId()).stream()
                .collect(Collectors.toMap(PurchaseLedgerItem::getId, Function.identity()));

        List<ReceivingDtos.PlanLine> plan = request.items();
        if (plan == null || plan.isEmpty()) {
            plan = new ArrayList<>();
            for (PurchaseLedgerItem item : ledgerItems.values()) {
                int open = openQuantity(item);
                if (item.getMatchStatus() == MatchStatus.MATCHED && item.getMatchedSkuId() != null && open > 0) {
                    plan.add(new ReceivingDtos.PlanLine(item.getId(), open));
                }
            }
        }
        List<String> warnings = new ArrayList<>();
        List<PurchaseReceiptItem> lines = new ArrayList<>();
        for (ReceivingDtos.PlanLine line : plan) {
            PurchaseLedgerItem item = ledgerItems.get(line.purchaseLedgerItemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "이 매입내역의 품목이 아닙니다. (id=" + line.purchaseLedgerItemId() + ")");
            }
            if (item.getMatchStatus() != MatchStatus.MATCHED || item.getMatchedSkuId() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        item.getProductName() + ": 상품 매칭을 먼저 확정해 주세요.");
            }
            Long unitCost = item.effectiveUnitCost();
            if (unitCost == null) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        item.getProductName() + ": 단가 또는 금액이 없어 원가를 계산할 수 없습니다.");
            }
            int open = openQuantity(item);
            if (line.expectedQuantity() > open) {
                warnings.add(item.getProductName() + ": 입고 예정 수량(" + line.expectedQuantity()
                        + ")이 미입고 수량(" + open + ")보다 많습니다.");
            }
            PurchaseReceiptItem receiptItem = new PurchaseReceiptItem();
            receiptItem.setPurchaseLedgerItemId(item.getId());
            receiptItem.setProductId(item.getMatchedProductId());
            receiptItem.setSkuId(item.getMatchedSkuId());
            receiptItem.setExpectedQuantity(line.expectedQuantity());
            receiptItem.setUnitCost(unitCost);
            lines.add(receiptItem);
        }
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "입고할 품목이 없습니다. 상품 매칭이 확정되고 미입고 수량이 남은 품목이 필요합니다.");
        }

        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setReceiptNo(newReceiptNo());
        receipt.setPurchaseLedgerId(ledger.getId());
        receipt.setSupplierId(ledger.getSupplierId());
        receipt.setExpectedDate(request.expectedDate());
        receipt.setFreightCost(nz(request.freightCost()));
        receipt.setIncidentalCost(nz(request.incidentalCost()));
        receipt.setPackagingCost(nz(request.packagingCost()));
        receipt.setOtherCost(nz(request.otherCost()));
        receipt.setAllocationMethod(request.allocationMethod() == null ? AllocationMethod.AMOUNT
                : request.allocationMethod());
        receipt.setMemo(trim(request.memo()));
        receipt.setCreatedBy(actorId);
        try {
            receipt = receiptRepository.saveAndFlush(receipt);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "입고번호 생성이 충돌했습니다. 다시 시도해 주세요.");
        }
        for (PurchaseReceiptItem line : lines) {
            line.setPurchaseReceiptId(receipt.getId());
            receiptItemRepository.save(line);
        }
        auditLogService.record(actorId, "PURCHASE_RECEIPT_CREATE", "PURCHASE_RECEIPT", String.valueOf(receipt.getId()),
                "receiptNo=" + receipt.getReceiptNo() + ", ledger=" + ledger.getManagementNo() + ", lines=" + lines.size());
        return toResponse(receipt, warnings, false);
    }

    @Transactional
    public ReceivingDtos.ReceiptResponse confirm(Long receiptId, ReceivingDtos.ConfirmReceiptRequest request,
                                                 Long actorId) {
        PurchaseReceipt receipt = require(receiptId);
        if (receipt.getStatus() == ReceiptStatus.CONFIRMED) {
            return toResponse(receipt, List.of("이미 입고 확정된 건입니다. 재고는 다시 반영되지 않았습니다."), true);
        }
        if (receipt.getStatus() == ReceiptStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "취소된 입고건은 확정할 수 없습니다.");
        }
        Map<Long, Integer> counted = new HashMap<>();
        if (request != null && request.items() != null) {
            for (ReceivingDtos.ConfirmLine line : request.items()) {
                counted.put(line.receiptItemId(), line.receivedQuantity());
            }
        }

        Instant now = clock.instant();
        if (receiptRepository.claimForConfirm(receiptId, now, actorId) == 0) {
            PurchaseReceipt current = require(receiptId);
            return toResponse(current, List.of("이미 입고 확정된 건입니다. 재고는 다시 반영되지 않았습니다."), true);
        }
        receipt = require(receiptId);
        if (request != null) {
            if (request.freightCost() != null) {
                receipt.setFreightCost(request.freightCost());
            }
            if (request.incidentalCost() != null) {
                receipt.setIncidentalCost(request.incidentalCost());
            }
            if (request.packagingCost() != null) {
                receipt.setPackagingCost(request.packagingCost());
            }
            if (request.otherCost() != null) {
                receipt.setOtherCost(request.otherCost());
            }
            if (request.allocationMethod() != null) {
                receipt.setAllocationMethod(request.allocationMethod());
            }
            if (request.memo() != null) {
                receipt.setMemo(trim(request.memo()));
            }
        }

        List<PurchaseReceiptItem> lines = receiptItemRepository.findByPurchaseReceiptIdOrderByIdAsc(receiptId);
        for (Long id : counted.keySet()) {
            if (lines.stream().noneMatch(l -> l.getId().equals(id))) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "이 입고건의 품목이 아닙니다. (id=" + id + ")");
            }
        }
        for (PurchaseReceiptItem line : lines) {
            line.setReceivedQuantity(counted.getOrDefault(line.getId(), line.getExpectedQuantity()));
        }
        allocate(lines, receipt);

        List<String> warnings = new ArrayList<>();
        int totalReceived = 0;
        for (PurchaseReceiptItem line : lines) {
            int qty = line.getReceivedQuantity();
            PurchaseLedgerItem ledgerItem = line.getPurchaseLedgerItemId() == null ? null
                    : ledgerItemRepository.findById(line.getPurchaseLedgerItemId()).orElse(null);
            String name = ledgerItem == null ? "SKU " + line.getSkuId() : ledgerItem.getProductName();
            if (qty != line.getExpectedQuantity()) {
                warnings.add(name + ": 실제 입고 " + qty + "개 / 예정 " + line.getExpectedQuantity() + "개 (차이 "
                        + (qty - line.getExpectedQuantity()) + ")");
            }
            if (qty > 0) {
                receiveStock(receipt, line, ledgerItem, qty, actorId, now);
                totalReceived += qty;
            }
            if (ledgerItem != null) {
                int remaining = ledgerItem.remainingQuantity();
                if (remaining > 0) {
                    warnings.add(name + ": 거래명세표 수량 대비 미입고 " + remaining + "개");
                } else if (ledgerItem.getQuantity() != null && ledgerItem.getReceivedQuantity() > ledgerItem.getQuantity()) {
                    warnings.add(name + ": 거래명세표 수량보다 " + (ledgerItem.getReceivedQuantity() - ledgerItem.getQuantity())
                            + "개 더 입고되었습니다.");
                }
            }
            receiptItemRepository.save(line);
        }
        receiptRepository.save(receipt);
        auditLogService.record(actorId, "PURCHASE_RECEIPT_CONFIRM", "PURCHASE_RECEIPT", String.valueOf(receiptId),
                "receiptNo=" + receipt.getReceiptNo() + ", receivedTotal=" + totalReceived + ", extraCost="
                        + receipt.additionalCostTotal());
        log.info("Purchase receipt confirmed receiptId={} receivedTotal={} actorId={}", receiptId, totalReceived, actorId);
        return toResponse(receipt, warnings, false);
    }

    private void receiveStock(PurchaseReceipt receipt, PurchaseReceiptItem line, PurchaseLedgerItem ledgerItem,
                              int qty, Long actorId, Instant now) {
        Inventory inventory = inventoryRepository.findBySkuId(line.getSkuId()).orElseGet(() -> {
            Inventory created = new Inventory();
            created.setSkuId(line.getSkuId());
            created.setStockQuantity(0);
            created.setReservedQuantity(0);
            created.setReorderPoint(5);
            created.setVersion(0L);
            created.setUpdatedAt(now);
            return inventoryRepository.saveAndFlush(created);
        });
        int stockBefore = inventory.getStockQuantity() == null ? 0 : inventory.getStockQuantity();
        inventory.restoreStock(qty);
        inventory.setUpdatedAt(now);
        try {
            inventoryRepository.saveAndFlush(inventory);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "재고가 다른 요청에 의해 변경되었습니다. 다시 시도해 주세요.");
        }
        try {
            movementWriter.write(new InventoryMovementWriter.Entry(line.getSkuId(), MovementType.RECEIPT, qty, REF_TYPE,
                    String.valueOf(line.getId()), "매입 입고 " + receipt.getReceiptNo(), actorId, now, stockBefore,
                    inventory.getStockQuantity(), receipt.getId(), line.getLandedUnitCost()));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 재고에 반영된 입고 품목입니다.");
        }
        BigDecimal allocatedUnit = line.getAllocatedCost() == null || qty == 0 ? BigDecimal.ZERO
                : line.getAllocatedCost().divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP);
        costService.recordReceipt(line.getProductId(), line.getSkuId(), line.getId(), receipt.getPurchaseLedgerId(),
                receipt.getSupplierId(), qty, BigDecimal.valueOf(line.getUnitCost()), allocatedUnit, stockBefore,
                actorId, now);
        if (ledgerItem != null) {
            ledgerItem.setReceivedQuantity(ledgerItem.getReceivedQuantity() + qty);
            ledgerItemRepository.save(ledgerItem);
        }
    }

    /**
     * Spreads the receipt's additional costs (freight, incidental, packaging, other) over received lines by
     * quantity or by purchase amount; the last line takes the rounding remainder so the total is exact.
     */
    static void allocate(List<PurchaseReceiptItem> lines, PurchaseReceipt receipt) {
        BigDecimal extra = BigDecimal.valueOf(receipt.additionalCostTotal());
        List<PurchaseReceiptItem> received = lines.stream().filter(l -> l.getReceivedQuantity() > 0).toList();
        BigDecimal baseTotal = BigDecimal.ZERO;
        for (PurchaseReceiptItem line : received) {
            baseTotal = baseTotal.add(base(line, receipt.getAllocationMethod()));
        }
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            PurchaseReceiptItem line = lines.get(i);
            int qty = line.getReceivedQuantity();
            if (qty <= 0) {
                line.setAllocatedCost(BigDecimal.ZERO);
                line.setLandedUnitCost(BigDecimal.valueOf(line.getUnitCost()).setScale(4, RoundingMode.HALF_UP));
                continue;
            }
            BigDecimal share;
            boolean last = line == received.get(received.size() - 1);
            if (extra.signum() == 0 || baseTotal.signum() == 0) {
                share = BigDecimal.ZERO;
            } else if (last) {
                share = extra.subtract(assigned);
            } else {
                share = extra.multiply(base(line, receipt.getAllocationMethod()))
                        .divide(baseTotal, 2, RoundingMode.HALF_UP);
            }
            assigned = assigned.add(share);
            line.setAllocatedCost(share.setScale(2, RoundingMode.HALF_UP));
            line.setLandedUnitCost(BigDecimal.valueOf(line.getUnitCost())
                    .add(share.divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP))
                    .setScale(4, RoundingMode.HALF_UP));
        }
    }

    private static BigDecimal base(PurchaseReceiptItem line, AllocationMethod method) {
        BigDecimal qty = BigDecimal.valueOf(line.getReceivedQuantity());
        return method == AllocationMethod.QUANTITY ? qty : qty.multiply(BigDecimal.valueOf(line.getUnitCost()));
    }

    @Transactional
    public ReceivingDtos.ReceiptResponse cancel(Long receiptId, Long actorId) {
        PurchaseReceipt receipt = require(receiptId);
        if (receipt.getStatus() != ReceiptStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "입고 예정 상태에서만 취소할 수 있습니다. 확정된 입고는 재고조정으로 정정해 주세요.");
        }
        receipt.setStatus(ReceiptStatus.CANCELLED);
        receipt.setCancelledAt(clock.instant());
        receipt.setCancelledBy(actorId);
        try {
            receiptRepository.saveAndFlush(receipt);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "입고건이 동시에 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
        auditLogService.record(actorId, "PURCHASE_RECEIPT_CANCEL", "PURCHASE_RECEIPT", String.valueOf(receiptId),
                "receiptNo=" + receipt.getReceiptNo());
        return toResponse(receipt, List.of(), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceivingDtos.ReceiptResponse> list(ReceiptStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
        Page<PurchaseReceipt> result = status == null ? receiptRepository.findAllByOrderByIdDesc(pageable)
                : receiptRepository.findByStatusOrderByIdDesc(status, pageable);
        return PageResponse.of(result.getContent().stream().map(r -> toResponse(r, List.of(), false)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ReceivingDtos.ReceiptResponse get(Long receiptId) {
        return toResponse(require(receiptId), List.of(), false);
    }

    private ReceivingDtos.ReceiptResponse toResponse(PurchaseReceipt receipt, List<String> warnings,
                                                     boolean alreadyConfirmed) {
        List<PurchaseReceiptItem> lines = receiptItemRepository.findByPurchaseReceiptIdOrderByIdAsc(receipt.getId());
        PurchaseLedger ledger = receipt.getPurchaseLedgerId() == null ? null
                : ledgerRepository.findById(receipt.getPurchaseLedgerId()).orElse(null);
        Map<Long, Product> products = productRepository.findAllById(
                        lines.stream().map(PurchaseReceiptItem::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        Map<Long, ProductSku> skus = skuRepository.findAllById(
                        lines.stream().map(PurchaseReceiptItem::getSkuId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductSku::getSkuId, Function.identity()));
        Map<Long, Integer> stock = new HashMap<>();
        if (!lines.isEmpty()) {
            for (Inventory inventory : inventoryRepository.findBySkuIdIn(
                    lines.stream().map(PurchaseReceiptItem::getSkuId).toList())) {
                stock.put(inventory.getSkuId(), inventory.getStockQuantity());
            }
        }
        List<ReceivingDtos.ReceiptItemResponse> items = new ArrayList<>();
        for (PurchaseReceiptItem line : lines) {
            PurchaseLedgerItem ledgerItem = line.getPurchaseLedgerItemId() == null ? null
                    : ledgerItemRepository.findById(line.getPurchaseLedgerItemId()).orElse(null);
            Product product = products.get(line.getProductId());
            ProductSku sku = skus.get(line.getSkuId());
            items.add(new ReceivingDtos.ReceiptItemResponse(line.getId(), line.getPurchaseLedgerItemId(),
                    line.getProductId(), product == null ? null : product.getProductName(), line.getSkuId(),
                    sku == null ? null : sku.getColor() + "/" + sku.getSize(),
                    ledgerItem == null || ledgerItem.getQuantity() == null ? 0 : ledgerItem.getQuantity(),
                    line.getExpectedQuantity(), line.getReceivedQuantity(),
                    ledgerItem == null ? 0 : ledgerItem.getReceivedQuantity(),
                    ledgerItem == null ? 0 : ledgerItem.remainingQuantity(), line.getUnitCost(),
                    line.getAllocatedCost(), line.getLandedUnitCost(), stock.getOrDefault(line.getSkuId(), 0)));
        }
        return new ReceivingDtos.ReceiptResponse(receipt.getId(), receipt.getReceiptNo(), receipt.getPurchaseLedgerId(),
                ledger == null ? null : ledger.getManagementNo(), ledger == null ? null : ledger.getSupplierName(),
                receipt.getStatus().name(), receipt.getExpectedDate(), receipt.getFreightCost(),
                receipt.getIncidentalCost(), receipt.getPackagingCost(), receipt.getOtherCost(),
                receipt.additionalCostTotal(), receipt.getAllocationMethod(), receipt.getMemo(),
                receipt.getConfirmedAt(), receipt.getConfirmedBy(), receipt.getCreatedAt(), items, warnings,
                alreadyConfirmed);
    }

    private int openQuantity(PurchaseLedgerItem item) {
        long pending = receiptItemRepository.pendingQuantityForLedgerItem(item.getId());
        return (int) Math.max(0, item.remainingQuantity() - pending);
    }

    private PurchaseReceipt require(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "입고건을 찾을 수 없습니다."));
    }

    private String newReceiptNo() {
        String date = clock.instant().atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                suffix.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            String candidate = "RC" + date + "-" + suffix.toString().toUpperCase(Locale.ROOT);
            if (!receiptRepository.existsByReceiptNo(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "입고번호를 만들지 못했습니다.");
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
