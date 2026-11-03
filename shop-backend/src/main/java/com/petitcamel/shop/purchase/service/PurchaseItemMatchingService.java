package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.domain.ProductStatus;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.MatchStatus;
import com.petitcamel.shop.purchase.domain.PurchaseLedger;
import com.petitcamel.shop.purchase.domain.PurchaseLedgerItem;
import com.petitcamel.shop.purchase.domain.SupplierProductMapping;
import com.petitcamel.shop.purchase.repository.PurchaseLedgerItemRepository;
import com.petitcamel.shop.purchase.repository.PurchaseLedgerRepository;
import com.petitcamel.shop.purchase.repository.SupplierProductMappingRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Matches purchase line items to catalog SKUs.
 * <ol>
 *   <li>supplier product code learned from earlier confirmations (supplier_product_mapping) → MATCHED</li>
 *   <li>supplier code equal to an internal SKU code → MATCHED</li>
 *   <li>same supplier product name confirmed before → MATCHED</li>
 *   <li>name similarity → CANDIDATE (admin chooses), otherwise NEW_PRODUCT</li>
 * </ol>
 * Automatic results are suggestions; receiving uses only what the admin confirmed or left as MATCHED.
 */
@Service
public class PurchaseItemMatchingService {

    private static final double CANDIDATE_THRESHOLD = 0.35;
    private static final int MAX_CANDIDATES = 5;

    public enum Action { AUTO, CONFIRM, NEW_PRODUCT, IGNORE, RESET }

    public record MatchRequest(
            Long ledgerId,
            Long itemId,
            @NotNull Action action,
            Long productId,
            Long skuId,
            @Size(max = 50) String color,
            @Size(max = 30) String size,
            Boolean remember
    ) {
    }

    public record SkuOption(Long skuId, String skuCode, String color, String size, int stockQuantity,
                            boolean optionMatches) {
    }

    public record Candidate(Long productId, String productName, String status, BigDecimal score,
                            List<String> reasons, List<SkuOption> skus) {
    }

    public record UnmatchedItem(
            Long itemId,
            Long ledgerId,
            String managementNo,
            String supplierName,
            java.time.LocalDate purchaseDate,
            String productName,
            String color,
            String size,
            String supplierProductCode,
            Integer quantity,
            Long unitPrice,
            MatchStatus matchStatus,
            BigDecimal matchScore,
            String matchNote
    ) {
    }

    private final PurchaseLedgerItemRepository itemRepository;
    private final PurchaseLedgerRepository ledgerRepository;
    private final SupplierProductMappingRepository mappingRepository;
    private final ProductRepository productRepository;
    private final ProductSkuRepository skuRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public PurchaseItemMatchingService(
            PurchaseLedgerItemRepository itemRepository,
            PurchaseLedgerRepository ledgerRepository,
            SupplierProductMappingRepository mappingRepository,
            ProductRepository productRepository,
            ProductSkuRepository skuRepository,
            InventoryRepository inventoryRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.itemRepository = itemRepository;
        this.ledgerRepository = ledgerRepository;
        this.mappingRepository = mappingRepository;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Runs automatic matching on the ledger's UNMATCHED items. */
    @Transactional
    public void autoMatchLedger(Long ledgerId) {
        PurchaseLedger ledger = ledgerRepository.findById(ledgerId).orElse(null);
        if (ledger == null) {
            return;
        }
        List<Product> catalog = null;
        for (PurchaseLedgerItem item : itemRepository.findByPurchaseLedgerIdOrderByLineNoAsc(ledgerId)) {
            if (item.getMatchStatus() != MatchStatus.UNMATCHED) {
                continue;
            }
            if (catalog == null) {
                catalog = productRepository.findAll();
            }
            autoMatch(ledger, item, catalog);
            itemRepository.save(item);
        }
    }

    void autoMatch(PurchaseLedger ledger, PurchaseLedgerItem item, List<Product> catalog) {
        Optional<ProductSku> exact = exactMatch(ledger, item);
        if (exact.isPresent()) {
            ProductSku sku = exact.get();
            setMatched(item, sku.getProductId(), sku.getSkuId(), BigDecimal.ONE, matchReason(ledger, item, sku));
            return;
        }
        List<Candidate> candidates = rankCandidates(item, catalog);
        if (candidates.isEmpty()) {
            clearMatch(item);
            item.setMatchStatus(MatchStatus.NEW_PRODUCT);
            item.setMatchNote("일치하거나 유사한 기존 상품이 없습니다. 신규 상품 등록 후보입니다.");
            return;
        }
        Candidate top = candidates.get(0);
        item.setMatchStatus(MatchStatus.CANDIDATE);
        item.setMatchedProductId(top.productId());
        item.setMatchedSkuId(top.skus().stream().filter(SkuOption::optionMatches).map(SkuOption::skuId)
                .findFirst().orElse(null));
        item.setMatchScore(top.score());
        item.setMatchNote("유사 상품 " + candidates.size() + "개: "
                + candidates.stream().map(Candidate::productName).collect(Collectors.joining(", ")));
    }

    private Optional<ProductSku> exactMatch(PurchaseLedger ledger, PurchaseLedgerItem item) {
        String code = trim(item.getSupplierProductCode());
        if (ledger.getSupplierId() != null && code != null) {
            Optional<ProductSku> byMapping = resolveMappings(mappingRepository.findByCode(ledger.getSupplierId(), code),
                    item);
            if (byMapping.isPresent()) {
                return byMapping;
            }
        }
        if (code != null) {
            Optional<ProductSku> bySkuCode = skuRepository.findBySkuCode(code);
            if (bySkuCode.isPresent()) {
                return bySkuCode;
            }
        }
        if (ledger.getSupplierId() != null && trim(item.getProductName()) != null) {
            return resolveMappings(mappingRepository.findByName(ledger.getSupplierId(), item.getProductName()), item);
        }
        return Optional.empty();
    }

    private Optional<ProductSku> resolveMappings(List<SupplierProductMapping> mappings, PurchaseLedgerItem item) {
        for (SupplierProductMapping mapping : mappings) {
            if (mapping.getSkuId() != null) {
                Optional<ProductSku> sku = skuRepository.findById(mapping.getSkuId());
                if (sku.isPresent() && optionMatches(sku.get(), item)) {
                    return sku;
                }
            }
        }
        for (SupplierProductMapping mapping : mappings) {
            Optional<ProductSku> sku = skuRepository.findByProductId(mapping.getProductId()).stream()
                    .filter(s -> optionMatches(s, item)).findFirst();
            if (sku.isPresent()) {
                return sku;
            }
        }
        return Optional.empty();
    }

    private String matchReason(PurchaseLedger ledger, PurchaseLedgerItem item, ProductSku sku) {
        String code = trim(item.getSupplierProductCode());
        if (code != null && code.equalsIgnoreCase(sku.getSkuCode())) {
            return "내부 SKU 코드 일치(" + sku.getSkuCode() + ")";
        }
        if (code != null) {
            return "거래처 상품코드 일치(" + code + ")";
        }
        return "이전 매입 이력의 거래처 상품명 일치";
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(Long itemId) {
        PurchaseLedgerItem item = requireItem(itemId);
        List<Candidate> ranked = new ArrayList<>(rankCandidates(item, productRepository.findAll()));
        if (item.getMatchedProductId() != null
                && ranked.stream().noneMatch(c -> c.productId().equals(item.getMatchedProductId()))) {
            productRepository.findById(item.getMatchedProductId()).ifPresent(p ->
                    ranked.add(0, toCandidate(p, item, BigDecimal.ONE, List.of("현재 연결된 상품"))));
        }
        return ranked;
    }

    List<Candidate> rankCandidates(PurchaseLedgerItem item, List<Product> catalog) {
        List<Candidate> result = new ArrayList<>();
        for (Product product : catalog) {
            if (product.getStatus() == ProductStatus.DISCONTINUED) {
                continue;
            }
            double score = ProductNameSimilarity.score(item.getProductName(), product.getProductName());
            if (score >= CANDIDATE_THRESHOLD) {
                result.add(toCandidate(product, item, BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                        List.of("상품명 유사도 " + Math.round(score * 100) + "%")));
            }
        }
        result.sort(Comparator.comparing(Candidate::score).reversed());
        return result.size() > MAX_CANDIDATES ? result.subList(0, MAX_CANDIDATES) : result;
    }

    private Candidate toCandidate(Product product, PurchaseLedgerItem item, BigDecimal score, List<String> reasons) {
        List<ProductSku> skus = skuRepository.findByProductId(product.getProductId());
        Map<Long, Integer> stock = new HashMap<>();
        if (!skus.isEmpty()) {
            for (Inventory inventory : inventoryRepository.findBySkuIdIn(skus.stream().map(ProductSku::getSkuId).toList())) {
                stock.put(inventory.getSkuId(), inventory.getStockQuantity());
            }
        }
        List<SkuOption> options = skus.stream()
                .map(s -> new SkuOption(s.getSkuId(), s.getSkuCode(), s.getColor(), s.getSize(),
                        stock.getOrDefault(s.getSkuId(), 0), optionMatches(s, item)))
                .toList();
        return new Candidate(product.getProductId(), product.getProductName(), product.getStatus().name(), score,
                reasons, options);
    }

    /** Applies an admin decision (or re-runs auto matching) for one item or a whole ledger. */
    @Transactional
    public List<UnmatchedItem> match(MatchRequest request, Long actorId) {
        if (request.action() == Action.AUTO) {
            if (request.ledgerId() == null && request.itemId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "매입내역 또는 품목을 지정해 주세요.");
            }
            if (request.itemId() != null) {
                PurchaseLedgerItem item = requireItem(request.itemId());
                if (item.getReceivedQuantity() == 0) {
                    item.setMatchStatus(MatchStatus.UNMATCHED);
                }
                itemRepository.save(item);
                autoMatchLedger(item.getPurchaseLedgerId());
                return toUnmatched(List.of(requireItem(request.itemId())));
            }
            autoMatchLedger(request.ledgerId());
            return toUnmatched(itemRepository.findByPurchaseLedgerIdOrderByLineNoAsc(request.ledgerId()));
        }
        if (request.itemId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "품목을 지정해 주세요.");
        }
        PurchaseLedgerItem item = requireItem(request.itemId());
        if (item.getReceivedQuantity() > 0 && request.action() != Action.CONFIRM) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "입고가 시작된 품목은 매칭을 해제할 수 없습니다.");
        }
        PurchaseLedger ledger = ledgerRepository.findById(item.getPurchaseLedgerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매입내역을 찾을 수 없습니다."));
        switch (request.action()) {
            case CONFIRM -> confirm(ledger, item, request, actorId);
            case NEW_PRODUCT -> {
                clearMatch(item);
                item.setMatchStatus(MatchStatus.NEW_PRODUCT);
                item.setMatchNote("관리자가 신규 상품으로 지정했습니다.");
            }
            case IGNORE -> {
                clearMatch(item);
                item.setMatchStatus(MatchStatus.IGNORED);
                item.setMatchNote("재고 관리 대상이 아닌 품목으로 지정했습니다.");
            }
            case RESET -> {
                clearMatch(item);
                item.setMatchStatus(MatchStatus.UNMATCHED);
            }
            default -> {
            }
        }
        itemRepository.save(item);
        auditLogService.record(actorId, "PURCHASE_ITEM_MATCH", "PURCHASE_LEDGER_ITEM", String.valueOf(item.getId()),
                "action=" + request.action() + ", productId=" + item.getMatchedProductId() + ", skuId="
                        + item.getMatchedSkuId());
        return toUnmatched(List.of(item));
    }

    private void confirm(PurchaseLedger ledger, PurchaseLedgerItem item, MatchRequest request, Long actorId) {
        if (request.productId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "연결할 상품을 선택해 주세요.");
        }
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다."));
        ProductSku sku;
        if (request.skuId() != null) {
            sku = skuRepository.findById(request.skuId())
                    .filter(s -> s.getProductId().equals(product.getProductId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품 옵션(SKU)을 찾을 수 없습니다."));
        } else {
            String color = firstNonBlank(request.color(), item.getColor());
            String size = firstNonBlank(request.size(), item.getSize());
            if (color == null || size == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "옵션을 선택하거나 새 옵션의 색상과 사이즈를 입력해 주세요.");
            }
            sku = findOrCreateSku(product, color, size);
        }
        if (item.getReceivedQuantity() > 0 && item.getMatchedSkuId() != null
                && !item.getMatchedSkuId().equals(sku.getSkuId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "입고가 시작된 품목은 다른 옵션으로 변경할 수 없습니다.");
        }
        setMatched(item, product.getProductId(), sku.getSkuId(), BigDecimal.ONE, "관리자 확인");
        if (!Boolean.FALSE.equals(request.remember()) && ledger.getSupplierId() != null) {
            remember(ledger, item, sku);
        }
    }

    private ProductSku findOrCreateSku(Product product, String color, String size) {
        Optional<ProductSku> existing = skuRepository.findByProductId(product.getProductId()).stream()
                .filter(s -> s.getColor().equalsIgnoreCase(color) && s.getSize().equalsIgnoreCase(size))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = clock.instant();
        String base = ("P" + product.getProductId() + "-" + color + "-" + size).toUpperCase(Locale.ROOT)
                .replaceAll("[^0-9A-Z가-힣-]", "");
        String code = base.length() > 56 ? base.substring(0, 56) : base;
        int suffix = 1;
        while (skuRepository.findBySkuCode(code).isPresent()) {
            code = (base.length() > 52 ? base.substring(0, 52) : base) + "-" + (++suffix);
        }
        ProductSku sku = new ProductSku();
        sku.setProductId(product.getProductId());
        sku.setSkuCode(code);
        sku.setColor(color.trim());
        sku.setSize(size.trim());
        sku.setAdditionalPrice(BigDecimal.ZERO);
        sku.setStatus(ProductStatus.ON_SALE);
        sku.setCreatedAt(now);
        sku.setUpdatedAt(now);
        ProductSku saved = skuRepository.save(sku);
        Inventory inventory = new Inventory();
        inventory.setSkuId(saved.getSkuId());
        inventory.setStockQuantity(0);
        inventory.setReservedQuantity(0);
        inventory.setReorderPoint(5);
        inventory.setVersion(0L);
        inventory.setUpdatedAt(now);
        inventoryRepository.save(inventory);
        return saved;
    }

    private void remember(PurchaseLedger ledger, PurchaseLedgerItem item, ProductSku sku) {
        String code = trim(item.getSupplierProductCode());
        List<SupplierProductMapping> existing = code != null
                ? mappingRepository.findByCode(ledger.getSupplierId(), code)
                : mappingRepository.findByName(ledger.getSupplierId(), item.getProductName());
        SupplierProductMapping mapping = existing.stream()
                .filter(m -> Objects.equals(m.getSkuId(), sku.getSkuId()))
                .findFirst()
                .orElseGet(SupplierProductMapping::new);
        mapping.setSupplierId(ledger.getSupplierId());
        mapping.setSupplierProductCode(code);
        mapping.setSupplierProductName(item.getProductName());
        mapping.setProductId(sku.getProductId());
        mapping.setSkuId(sku.getSkuId());
        mapping.setColor(sku.getColor());
        mapping.setSize(sku.getSize());
        mapping.setLastUnitCost(item.effectiveUnitCost());
        mapping.setLastPurchaseDate(ledger.getPurchaseDate());
        mappingRepository.save(mapping);
    }

    @Transactional(readOnly = true)
    public PageResponse<UnmatchedItem> unmatched(int page, int size) {
        Page<PurchaseLedgerItem> result = itemRepository.findOpenByMatchStatus(
                EnumSet.of(MatchStatus.UNMATCHED, MatchStatus.CANDIDATE, MatchStatus.NEW_PRODUCT),
                PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100)));
        return PageResponse.of(toUnmatched(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    private List<UnmatchedItem> toUnmatched(List<PurchaseLedgerItem> items) {
        Map<Long, PurchaseLedger> ledgers = ledgerRepository.findAllById(
                        items.stream().map(PurchaseLedgerItem::getPurchaseLedgerId).distinct().toList()).stream()
                .collect(Collectors.toMap(PurchaseLedger::getId, l -> l));
        return items.stream().map(i -> {
            PurchaseLedger l = ledgers.get(i.getPurchaseLedgerId());
            return new UnmatchedItem(i.getId(), i.getPurchaseLedgerId(), l == null ? null : l.getManagementNo(),
                    l == null ? null : l.getSupplierName(), l == null ? null : l.getPurchaseDate(), i.getProductName(),
                    i.getColor(), i.getSize(), i.getSupplierProductCode(), i.getQuantity(), i.effectiveUnitCost(),
                    i.getMatchStatus(), i.getMatchScore(), i.getMatchNote());
        }).toList();
    }

    private PurchaseLedgerItem requireItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매입 품목을 찾을 수 없습니다."));
    }

    private static void setMatched(PurchaseLedgerItem item, Long productId, Long skuId, BigDecimal score, String note) {
        item.setMatchStatus(MatchStatus.MATCHED);
        item.setMatchedProductId(productId);
        item.setMatchedSkuId(skuId);
        item.setMatchScore(score);
        item.setMatchNote(note);
    }

    private static void clearMatch(PurchaseLedgerItem item) {
        item.setMatchedProductId(null);
        item.setMatchedSkuId(null);
        item.setMatchScore(null);
        item.setMatchNote(null);
    }

    /** Colour / size equality, ignoring case and spaces; a blank side matches anything. */
    static boolean optionMatches(ProductSku sku, PurchaseLedgerItem item) {
        return loosely(sku.getColor(), item.getColor()) && loosely(sku.getSize(), item.getSize());
    }

    private static boolean loosely(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            return true;
        }
        return a.replace(" ", "").equalsIgnoreCase(b.replace(" ", ""));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b == null || b.isBlank() ? null : b.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
