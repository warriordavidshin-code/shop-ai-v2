package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.ai.service.AiUsageLogService;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EntrySource;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceType;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.KeptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.VerificationStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEvidence;
import com.petitcamel.shop.purchase.domain.PurchaseLedger;
import com.petitcamel.shop.purchase.domain.PurchaseLedgerItem;
import com.petitcamel.shop.purchase.domain.PurchaseReceipt;
import com.petitcamel.shop.purchase.domain.Supplier;
import com.petitcamel.shop.purchase.dto.PurchaseDtos;
import com.petitcamel.shop.purchase.repository.PurchaseEvidenceRepository;
import com.petitcamel.shop.purchase.repository.PurchaseLedgerItemRepository;
import com.petitcamel.shop.purchase.repository.PurchaseLedgerRepository;
import com.petitcamel.shop.purchase.repository.PurchaseReceiptItemRepository;
import com.petitcamel.shop.purchase.repository.PurchaseReceiptRepository;
import com.petitcamel.shop.purchase.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품매입장부: CRUD, management numbers (YYYYMM-NNN), the sheet's 자료 확인 rule, duplicate detection and the
 * 월별요약 aggregation. Entries are soft-deleted; an entry with confirmed receiving cannot be deleted.
 */
@Service
public class PurchaseLedgerService {

    private static final Map<String, String> SORTABLE = Map.of(
            "managementNo", "managementNo",
            "purchaseDate", "purchaseDate",
            "supplierName", "supplierName",
            "purchaseAmount", "purchaseAmount",
            "paymentAmount", "paymentAmount",
            "paymentDate", "paymentDate",
            "createdAt", "createdAt");

    public record LedgerFilter(
            LocalDate from,
            LocalDate to,
            String month,
            Integer year,
            String supplier,
            Long supplierId,
            EvidenceType evidenceType,
            VerificationStatus verification,
            String keyword
    ) {
        public static LedgerFilter empty() {
            return new LedgerFilter(null, null, null, null, null, null, null, null, null);
        }
    }

    private final PurchaseLedgerRepository ledgerRepository;
    private final PurchaseLedgerItemRepository itemRepository;
    private final PurchaseEvidenceRepository evidenceRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseReceiptItemRepository receiptItemRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierService supplierService;
    private final PurchaseEvidenceService evidenceService;
    private final PurchaseItemMatchingService matchingService;
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final AiUsageLogService aiUsageLogService;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    public PurchaseLedgerService(
            PurchaseLedgerRepository ledgerRepository,
            PurchaseLedgerItemRepository itemRepository,
            PurchaseEvidenceRepository evidenceRepository,
            PurchaseReceiptRepository receiptRepository,
            PurchaseReceiptItemRepository receiptItemRepository,
            SupplierRepository supplierRepository,
            SupplierService supplierService,
            PurchaseEvidenceService evidenceService,
            PurchaseItemMatchingService matchingService,
            ProductRepository productRepository,
            ProductSkuRepository productSkuRepository,
            AiUsageLogService aiUsageLogService,
            AuditLogService auditLogService,
            EntityManager entityManager) {
        this.ledgerRepository = ledgerRepository;
        this.itemRepository = itemRepository;
        this.evidenceRepository = evidenceRepository;
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.supplierRepository = supplierRepository;
        this.supplierService = supplierService;
        this.evidenceService = evidenceService;
        this.matchingService = matchingService;
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.aiUsageLogService = aiUsageLogService;
        this.auditLogService = auditLogService;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<PurchaseDtos.LedgerRow> list(LedgerFilter filter, int page, int size, String sort) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 200), toSort(sort));
        Page<PurchaseLedger> result = ledgerRepository.findAll(spec(filter), pageable);
        return PageResponse.of(toRows(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    /** All matching rows in ledger order (management number), for the Excel export. */
    @Transactional(readOnly = true)
    public List<PurchaseLedger> search(LedgerFilter filter, int limit) {
        return ledgerRepository.findAll(spec(filter),
                        PageRequest.of(0, limit, Sort.by("purchaseDate", "managementNo").ascending()))
                .getContent();
    }

    @Transactional(readOnly = true)
    public PurchaseDtos.LedgerDetail get(Long id) {
        PurchaseLedger ledger = require(id);
        List<PurchaseLedgerItem> items = itemRepository.findByPurchaseLedgerIdOrderByLineNoAsc(id);
        List<PurchaseEvidence> evidences = evidenceRepository.findByPurchaseLedgerIdOrderByIdAsc(id);
        List<PurchaseDtos.ReceiptBrief> receipts = receiptRepository.findByPurchaseLedgerIdOrderByIdAsc(id).stream()
                .map(r -> new PurchaseDtos.ReceiptBrief(r.getId(), r.getReceiptNo(), r.getStatus().name(),
                        r.getConfirmedAt()))
                .toList();
        return new PurchaseDtos.LedgerDetail(toRows(List.of(ledger)).get(0), toItemResponses(items),
                evidenceService.toResponses(evidences), receipts, ledger.getSourceAnalysisId());
    }

    @Transactional(readOnly = true)
    public List<PurchaseLedgerItem> items(Long ledgerId) {
        return itemRepository.findByPurchaseLedgerIdOrderByLineNoAsc(ledgerId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<String>> evidenceNamesByLedger(List<Long> ledgerIds) {
        if (ledgerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> names = new HashMap<>();
        for (PurchaseEvidence evidence : evidenceRepository.findByPurchaseLedgerIdIn(ledgerIds)) {
            names.computeIfAbsent(evidence.getPurchaseLedgerId(), k -> new ArrayList<>())
                    .add(evidence.getOriginalFileName());
        }
        return names;
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public PurchaseDtos.LedgerDetail create(PurchaseDtos.LedgerRequest request, Long actorId) {
        guardDuplicates(request, null);
        PurchaseLedger ledger = new PurchaseLedger();
        ledger.setManagementNo(nextManagementNo(request.purchaseDate()));
        ledger.setCreatedBy(actorId);
        ledger.setEntrySource(request.sourceAnalysisId() != null ? EntrySource.AI : EntrySource.MANUAL);
        ledger.setSourceAnalysisId(request.sourceAnalysisId());
        apply(ledger, request, actorId);
        try {
            ledger = ledgerRepository.saveAndFlush(ledger);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "관리번호가 중복되었습니다. 다시 저장해 주세요.");
        }
        saveItems(ledger, request.items());
        refreshSummaryIfBlank(ledger, request);
        evidenceService.attachAll(request.evidenceIds(), ledger.getId());
        ledgerRepository.save(ledger);
        matchingService.autoMatchLedger(ledger.getId());
        if (request.sourceAnalysisId() != null) {
            aiUsageLogService.linkTarget(request.sourceAnalysisId(), "PURCHASE_LEDGER", ledger.getId());
        }
        auditLogService.record(actorId, "PURCHASE_LEDGER_CREATE", "PURCHASE_LEDGER", String.valueOf(ledger.getId()),
                "managementNo=" + ledger.getManagementNo() + ", source=" + ledger.getEntrySource()
                        + ", items=" + (request.items() == null ? 0 : request.items().size()));
        return get(ledger.getId());
    }

    @Transactional
    public PurchaseDtos.LedgerDetail update(Long id, PurchaseDtos.LedgerRequest request, Long actorId) {
        PurchaseLedger ledger = require(id);
        if (request.version() != null && !request.version().equals(ledger.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.");
        }
        guardDuplicates(request, id);
        String before = snapshot(ledger);
        String previousNo = ledger.getManagementNo();
        if (!YearMonth.from(ledger.getPurchaseDate()).equals(YearMonth.from(request.purchaseDate()))) {
            ledger.setManagementNo(nextManagementNo(request.purchaseDate()));
        }
        apply(ledger, request, actorId);
        if (request.items() != null) {
            saveItems(ledger, request.items());
        }
        refreshSummaryIfBlank(ledger, request);
        evidenceService.attachAll(request.evidenceIds(), ledger.getId());
        try {
            ledgerRepository.saveAndFlush(ledger);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.");
        }
        matchingService.autoMatchLedger(ledger.getId());
        String detail = "before={" + before + "}, after={" + snapshot(ledger) + "}";
        if (!previousNo.equals(ledger.getManagementNo())) {
            detail = "managementNo " + previousNo + " -> " + ledger.getManagementNo() + ", " + detail;
        }
        auditLogService.record(actorId, "PURCHASE_LEDGER_UPDATE", "PURCHASE_LEDGER", String.valueOf(id), detail);
        return get(id);
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        PurchaseLedger ledger = require(id);
        boolean received = receiptRepository.findByPurchaseLedgerIdOrderByIdAsc(id).stream()
                .anyMatch(r -> r.getStatus() == ReceiptStatus.CONFIRMED);
        if (received) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "입고 확정된 매입내역은 삭제할 수 없습니다. 재고조정·정정으로 처리해 주세요.");
        }
        for (PurchaseReceipt receipt : receiptRepository.findByPurchaseLedgerIdOrderByIdAsc(id)) {
            if (receipt.getStatus() == ReceiptStatus.PENDING) {
                receipt.setStatus(ReceiptStatus.CANCELLED);
                receipt.setCancelledAt(java.time.Instant.now());
                receipt.setCancelledBy(actorId);
                receiptRepository.save(receipt);
            }
        }
        ledger.setDeletedAt(java.time.Instant.now());
        ledger.setDeletedBy(actorId);
        ledgerRepository.save(ledger);
        auditLogService.record(actorId, "PURCHASE_LEDGER_DELETE", "PURCHASE_LEDGER", String.valueOf(id),
                "managementNo=" + ledger.getManagementNo() + ", " + snapshot(ledger));
    }

    // ------------------------------------------------------------------ duplicates

    @Transactional(readOnly = true)
    public List<PurchaseDtos.DuplicateCandidate> findDuplicates(PurchaseDtos.DuplicateCheckRequest request) {
        Map<Long, Set<String>> reasons = new LinkedHashMap<>();
        Map<Long, PurchaseLedger> ledgers = new HashMap<>();
        if (request.evidenceIds() != null) {
            for (Long evidenceId : request.evidenceIds()) {
                evidenceRepository.findById(evidenceId).ifPresent(evidence -> {
                    for (PurchaseEvidence same : evidenceRepository.findByFileHash(evidence.getFileHash())) {
                        if (same.getPurchaseLedgerId() != null) {
                            addReason(reasons, ledgers, same.getPurchaseLedgerId(),
                                    "같은 증빙 파일(" + same.getOriginalFileName() + ")이 첨부되어 있습니다.");
                        }
                    }
                });
            }
        }
        if (request.supplierName() != null && !request.supplierName().isBlank() && request.purchaseDate() != null
                && request.purchaseAmount() != null) {
            for (PurchaseLedger same : ledgerRepository.findSameSupplierDateAmount(request.supplierName(),
                    request.purchaseDate(), request.purchaseAmount())) {
                ledgers.put(same.getId(), same);
                addReason(reasons, ledgers, same.getId(), "거래처·매입일·매입액이 같습니다.");
            }
        }
        if (request.transferReference() != null && !request.transferReference().isBlank()) {
            Long amount = request.paymentAmount() != null ? request.paymentAmount() : request.purchaseAmount();
            if (amount != null) {
                for (PurchaseLedger same : ledgerRepository.findSameTransferAndAmount(request.transferReference().trim(),
                        amount)) {
                    ledgers.put(same.getId(), same);
                    addReason(reasons, ledgers, same.getId(), "이체 식별번호와 금액이 같습니다.");
                }
            }
        }
        List<PurchaseDtos.DuplicateCandidate> result = new ArrayList<>();
        reasons.forEach((ledgerId, why) -> {
            if (Objects.equals(ledgerId, request.excludeId())) {
                return;
            }
            PurchaseLedger ledger = ledgers.computeIfAbsent(ledgerId, k -> ledgerRepository.findById(k).orElse(null));
            if (ledger == null || ledger.isDeleted()) {
                return;
            }
            result.add(new PurchaseDtos.DuplicateCandidate(ledger.getId(), ledger.getManagementNo(),
                    ledger.getPurchaseDate(), ledger.getSupplierName(), ledger.getPurchaseAmount(), List.copyOf(why)));
        });
        return result;
    }

    private void addReason(Map<Long, Set<String>> reasons, Map<Long, PurchaseLedger> ledgers, Long ledgerId,
                           String reason) {
        reasons.computeIfAbsent(ledgerId, k -> new java.util.LinkedHashSet<>()).add(reason);
    }

    private void guardDuplicates(PurchaseDtos.LedgerRequest request, Long excludeId) {
        if (request.confirmDuplicate()) {
            return;
        }
        List<PurchaseDtos.DuplicateCandidate> duplicates = findDuplicates(new PurchaseDtos.DuplicateCheckRequest(
                excludeId, request.supplierName(), request.purchaseDate(), request.purchaseAmount(),
                request.paymentAmount(), request.transferReference(), request.evidenceIds()));
        if (!duplicates.isEmpty()) {
            String numbers = duplicates.stream().map(PurchaseDtos.DuplicateCandidate::managementNo)
                    .collect(Collectors.joining(", "));
            throw new BusinessException(ErrorCode.CONFLICT,
                    "중복 가능성이 있는 매입내역이 있습니다: " + numbers + ". 확인 후 다시 저장해 주세요.");
        }
    }

    // ------------------------------------------------------------------ summary (월별요약)

    @Transactional(readOnly = true)
    public PurchaseDtos.SummaryResponse summary(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = from.plusYears(1);
        long[][] byMonth = new long[13][8];

        @SuppressWarnings("unchecked")
        List<Object[]> purchaseRows = entityManager.createNativeQuery("""
                SELECT EXTRACT(MONTH FROM purchase_date)::int AS m,
                       COALESCE(SUM(purchase_amount), 0),
                       COUNT(*),
                       COUNT(*) FILTER (WHERE statement_kept = 'KEPT'),
                       COUNT(*) FILTER (WHERE transfer_kept = 'KEPT'),
                       COALESCE(SUM(difference_amount), 0),
                       COUNT(*) FILTER (WHERE verification_status = 'NEEDS_REVIEW'),
                       COUNT(*) FILTER (WHERE statement_kept = 'NOT_KEPT' OR transfer_kept = 'NOT_KEPT')
                FROM purchase_ledger
                WHERE deleted_at IS NULL AND purchase_date >= :from AND purchase_date < :to
                GROUP BY 1
                """).setParameter("from", from).setParameter("to", to).getResultList();
        for (Object[] row : purchaseRows) {
            int m = ((Number) row[0]).intValue();
            byMonth[m][0] = num(row[1]);
            byMonth[m][2] = num(row[2]);
            byMonth[m][3] = num(row[3]);
            byMonth[m][4] = num(row[4]);
            byMonth[m][5] = num(row[5]);
            byMonth[m][6] = num(row[6]);
            byMonth[m][7] = num(row[7]);
        }
        // 지급액 is grouped by 지급일, exactly like the sheet's SUMIFS on column H.
        @SuppressWarnings("unchecked")
        List<Object[]> paymentRows = entityManager.createNativeQuery("""
                SELECT EXTRACT(MONTH FROM payment_date)::int, COALESCE(SUM(payment_amount), 0)
                FROM purchase_ledger
                WHERE deleted_at IS NULL AND payment_date >= :from AND payment_date < :to
                GROUP BY 1
                """).setParameter("from", from).setParameter("to", to).getResultList();
        for (Object[] row : paymentRows) {
            byMonth[((Number) row[0]).intValue()][1] = num(row[1]);
        }

        List<PurchaseDtos.MonthSummary> months = new ArrayList<>();
        long[] total = new long[8];
        for (int m = 1; m <= 12; m++) {
            long[] v = byMonth[m];
            months.add(new PurchaseDtos.MonthSummary(m, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]));
            for (int i = 0; i < 8; i++) {
                total[i] += v[i];
            }
        }

        @SuppressWarnings("unchecked")
        List<Object[]> supplierRows = entityManager.createNativeQuery("""
                SELECT COALESCE(NULLIF(TRIM(supplier_name), ''), '(거래처 미입력)'), COUNT(*),
                       COALESCE(SUM(purchase_amount), 0), COALESCE(SUM(payment_amount), 0)
                FROM purchase_ledger
                WHERE deleted_at IS NULL AND purchase_date >= :from AND purchase_date < :to
                GROUP BY 1 ORDER BY 3 DESC LIMIT 20
                """).setParameter("from", from).setParameter("to", to).getResultList();
        List<PurchaseDtos.SupplierTotal> suppliers = supplierRows.stream()
                .map(r -> new PurchaseDtos.SupplierTotal((String) r[0], num(r[1]), num(r[2]), num(r[3])))
                .toList();

        long unlinked = evidenceRepository.findByPurchaseLedgerIdIsNullOrderByIdDesc(PageRequest.of(0, 1))
                .getTotalElements();
        return new PurchaseDtos.SummaryResponse(year, months, new PurchaseDtos.MonthSummary(0, total[0], total[1],
                total[2], total[3], total[4], total[5], total[6], total[7]), suppliers, unlinked);
    }

    // ------------------------------------------------------------------ helpers

    public PurchaseLedger require(Long id) {
        return ledgerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매입내역을 찾을 수 없습니다."));
    }

    /** Next YYYYMM-NNN for the purchase month; atomic per month via an upsert counter. */
    String nextManagementNo(LocalDate purchaseDate) {
        String yearMonth = purchaseDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        Number seq = (Number) entityManager.createNativeQuery("""
                INSERT INTO purchase_ledger_sequence (year_month, last_seq) VALUES (:ym, 1)
                ON CONFLICT (year_month) DO UPDATE SET last_seq = purchase_ledger_sequence.last_seq + 1
                RETURNING last_seq
                """).setParameter("ym", yearMonth).getSingleResult();
        return String.format(Locale.ROOT, "%s-%03d", yearMonth, seq.intValue());
    }

    private void apply(PurchaseLedger ledger, PurchaseDtos.LedgerRequest request, Long actorId) {
        Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처를 찾을 수 없습니다."));
        }
        String businessNo = normalizeBusinessNo(request.supplierBusinessNo());
        String supplierName = trimToNull(request.supplierName());
        if (supplier == null && supplierName != null) {
            supplier = supplierService.resolveOrCreate(supplierName, businessNo);
        }
        if (supplier != null) {
            if (supplierName == null) {
                supplierName = supplier.getSupplierName();
            }
            if (businessNo == null) {
                businessNo = supplier.getBusinessNo();
            }
        }
        ledger.setPurchaseDate(request.purchaseDate());
        ledger.setSupplierId(supplier == null ? null : supplier.getId());
        ledger.setSupplierName(supplierName);
        ledger.setSupplierBusinessNo(businessNo);
        ledger.setItemSummary(trimToNull(request.itemSummary()));
        ledger.setPurchaseAmount(request.purchaseAmount());
        ledger.setSupplyAmount(request.supplyAmount());
        ledger.setTaxAmount(request.taxAmount());
        ledger.setPaymentAmount(request.paymentAmount());
        ledger.setPaymentDate(request.paymentDate());
        ledger.setPaymentMethod(request.paymentMethod());
        ledger.setRecipientName(trimToNull(request.recipientName()));
        ledger.setTransferReference(trimToNull(request.transferReference()));
        ledger.setStatementKept(request.statementKept() == null ? KeptStatus.NOT_KEPT : request.statementKept());
        ledger.setTransferKept(request.transferKept() == null ? KeptStatus.NOT_KEPT : request.transferKept());
        ledger.setEvidenceType(request.evidenceType() == null ? EvidenceType.NONE : request.evidenceType());
        ledger.setEvidenceLocation(trimToNull(request.evidenceLocation()));
        ledger.setRemarks(trimToNull(request.remarks()));
        ledger.setUpdatedBy(actorId);
        ledger.refreshVerificationStatus();
    }

    private void refreshSummaryIfBlank(PurchaseLedger ledger, PurchaseDtos.LedgerRequest request) {
        if (trimToNull(request.itemSummary()) != null) {
            return;
        }
        List<PurchaseLedgerItem> items = itemRepository.findByPurchaseLedgerIdOrderByLineNoAsc(ledger.getId());
        if (!items.isEmpty()) {
            String summary = buildItemSummary(items);
            ledger.setItemSummary(summary.length() > 4000 ? summary.substring(0, 4000) : summary);
        }
    }

    /** Sheet format, one line per item: {@code 모건버뮤다/검/FREE 1×29,000원}. */
    public static String buildItemSummary(List<PurchaseLedgerItem> items) {
        return items.stream().map(item -> {
            StringBuilder line = new StringBuilder(item.getProductName());
            String option = optionText(item);
            if (option != null) {
                line.append('/').append(option);
            }
            if (item.getQuantity() != null || item.getUnitPrice() != null) {
                line.append(' ').append(item.getQuantity() == null ? "?" : item.getQuantity()).append('×');
                line.append(item.getUnitPrice() == null ? "?" : String.format(Locale.ROOT, "%,d", item.getUnitPrice()))
                        .append('원');
            } else if (item.getTotalAmount() != null) {
                line.append(' ').append(String.format(Locale.ROOT, "%,d", item.getTotalAmount())).append('원');
            }
            return line.toString();
        }).collect(Collectors.joining("\n"));
    }

    /** Same format as {@link #buildItemSummary} for an unsaved draft. */
    public static String buildItemSummaryFromRequests(List<PurchaseDtos.ItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<PurchaseLedgerItem> drafts = new ArrayList<>();
        for (PurchaseDtos.ItemRequest request : items) {
            PurchaseLedgerItem item = new PurchaseLedgerItem();
            item.setProductName(request.productName());
            item.setOptionName(request.optionName());
            item.setColor(request.color());
            item.setSize(request.size());
            item.setQuantity(request.quantity());
            item.setUnitPrice(request.unitPrice());
            item.setTotalAmount(request.totalAmount());
            drafts.add(item);
        }
        String summary = buildItemSummary(drafts);
        return summary.length() > 4000 ? summary.substring(0, 4000) : summary;
    }

    static String optionText(PurchaseLedgerItem item) {
        if (item.getOptionName() != null && !item.getOptionName().isBlank()) {
            return item.getOptionName().trim();
        }
        List<String> parts = new ArrayList<>();
        if (item.getColor() != null && !item.getColor().isBlank()) {
            parts.add(item.getColor().trim());
        }
        if (item.getSize() != null && !item.getSize().isBlank()) {
            parts.add(item.getSize().trim());
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    /**
     * Updates items in place by id (keeping match / receiving state), inserts new ones and removes dropped
     * ones. Items that already have stock received or pending cannot be removed.
     */
    private void saveItems(PurchaseLedger ledger, List<PurchaseDtos.ItemRequest> requests) {
        List<PurchaseLedgerItem> existing = itemRepository.findByPurchaseLedgerIdOrderByLineNoAsc(ledger.getId());
        Map<Long, PurchaseLedgerItem> byId = existing.stream()
                .collect(Collectors.toMap(PurchaseLedgerItem::getId, Function.identity()));
        List<PurchaseDtos.ItemRequest> safeRequests = requests == null ? List.of() : requests;
        Set<Long> keep = new HashSet<>();
        for (PurchaseDtos.ItemRequest request : safeRequests) {
            if (request.id() != null) {
                if (!byId.containsKey(request.id())) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "품목을 찾을 수 없습니다. (id=" + request.id() + ")");
                }
                keep.add(request.id());
            }
        }
        for (PurchaseLedgerItem item : existing) {
            if (!keep.contains(item.getId())) {
                if (item.getReceivedQuantity() > 0 || receiptItemRepository.pendingQuantityForLedgerItem(item.getId()) > 0) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "입고 처리된 품목(" + item.getProductName() + ")은 삭제할 수 없습니다.");
                }
                itemRepository.delete(item);
            } else {
                item.setLineNo(-item.getLineNo() - 1000);
            }
        }
        itemRepository.flush();

        int lineNo = 1;
        for (PurchaseDtos.ItemRequest request : safeRequests) {
            PurchaseLedgerItem item = request.id() == null ? new PurchaseLedgerItem() : byId.get(request.id());
            boolean identityChanged = item.getId() != null && (!Objects.equals(item.getProductName(),
                    request.productName().trim()) || !Objects.equals(item.getColor(), trimToNull(request.color()))
                    || !Objects.equals(item.getSize(), trimToNull(request.size())));
            item.setPurchaseLedgerId(ledger.getId());
            item.setLineNo(lineNo++);
            item.setProductName(request.productName().trim());
            item.setOptionName(trimToNull(request.optionName()));
            item.setColor(trimToNull(request.color()));
            item.setSize(trimToNull(request.size()));
            item.setSupplierProductCode(trimToNull(request.supplierProductCode()));
            if (request.quantity() != null && item.getReceivedQuantity() != null
                    && request.quantity() < item.getReceivedQuantity()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        item.getProductName() + ": 수량은 이미 입고된 수량(" + item.getReceivedQuantity() + ")보다 작을 수 없습니다.");
            }
            item.setQuantity(request.quantity());
            item.setUnitPrice(request.unitPrice());
            item.setSupplyAmount(request.supplyAmount());
            item.setTaxAmount(request.taxAmount());
            item.setTotalAmount(request.totalAmount());
            if (identityChanged && item.getReceivedQuantity() == 0) {
                item.setMatchStatus(com.petitcamel.shop.purchase.domain.PurchaseEnums.MatchStatus.UNMATCHED);
                item.setMatchedProductId(null);
                item.setMatchedSkuId(null);
                item.setMatchScore(null);
                item.setMatchNote(null);
            }
            itemRepository.save(item);
        }
        itemRepository.flush();
    }

    private List<PurchaseDtos.LedgerRow> toRows(List<PurchaseLedger> ledgers) {
        List<Long> ids = ledgers.stream().map(PurchaseLedger::getId).toList();
        Map<Long, Integer> evidenceCounts = new HashMap<>();
        Map<Long, Integer> itemCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (PurchaseEvidence e : evidenceRepository.findByPurchaseLedgerIdIn(ids)) {
                evidenceCounts.merge(e.getPurchaseLedgerId(), 1, Integer::sum);
            }
            for (PurchaseLedgerItem i : itemRepository.findByPurchaseLedgerIdIn(ids)) {
                itemCounts.merge(i.getPurchaseLedgerId(), 1, Integer::sum);
            }
        }
        return ledgers.stream().map(l -> new PurchaseDtos.LedgerRow(l.getId(), l.getManagementNo(),
                l.getPurchaseDate(), l.getSupplierId(), l.getSupplierName(), l.getSupplierBusinessNo(),
                l.getItemSummary(), l.getPurchaseAmount(), l.getSupplyAmount(), l.getTaxAmount(),
                l.getPaymentAmount(), l.getPaymentDate(), l.getPaymentMethod(), l.getRecipientName(),
                l.getTransferReference(), l.differenceAmount(), l.getStatementKept(), l.getTransferKept(),
                l.getEvidenceType(), l.getEvidenceLocation(), l.getRemarks(), l.getVerificationStatus(),
                l.getVerificationStatus().label(), l.missingFields(), l.getEntrySource(),
                evidenceCounts.getOrDefault(l.getId(), 0), itemCounts.getOrDefault(l.getId(), 0), l.getCreatedAt(),
                l.getUpdatedAt(), l.getVersion())).toList();
    }

    public List<PurchaseDtos.ItemResponse> toItemResponses(List<PurchaseLedgerItem> items) {
        Set<Long> productIds = new HashSet<>();
        Set<Long> skuIds = new HashSet<>();
        for (PurchaseLedgerItem item : items) {
            if (item.getMatchedProductId() != null) {
                productIds.add(item.getMatchedProductId());
            }
            if (item.getMatchedSkuId() != null) {
                skuIds.add(item.getMatchedSkuId());
            }
        }
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));
        Map<Long, ProductSku> skus = productSkuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getSkuId, Function.identity()));
        return items.stream().map(i -> {
            Product product = i.getMatchedProductId() == null ? null : products.get(i.getMatchedProductId());
            ProductSku sku = i.getMatchedSkuId() == null ? null : skus.get(i.getMatchedSkuId());
            return new PurchaseDtos.ItemResponse(i.getId(), i.getLineNo(), i.getProductName(), i.getOptionName(),
                    i.getColor(), i.getSize(), i.getSupplierProductCode(), i.getQuantity(), i.getUnitPrice(),
                    i.getSupplyAmount(), i.getTaxAmount(), i.getTotalAmount(), i.getMatchStatus(),
                    i.getMatchedProductId(), product == null ? null : product.getProductName(), i.getMatchedSkuId(),
                    sku == null ? null : sku.getColor() + "/" + sku.getSize(), i.getMatchScore(), i.getMatchNote(),
                    i.getReceivedQuantity(), i.remainingQuantity());
        }).toList();
    }

    private static Specification<PurchaseLedger> spec(LedgerFilter filter) {
        LedgerFilter f = filter == null ? LedgerFilter.empty() : filter;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), f.to()));
            }
            if (f.month() != null && !f.month().isBlank()) {
                YearMonth ym = YearMonth.parse(f.month().trim());
                predicates.add(cb.between(root.get("purchaseDate"), ym.atDay(1), ym.atEndOfMonth()));
            } else if (f.year() != null) {
                predicates.add(cb.between(root.get("purchaseDate"), LocalDate.of(f.year(), 1, 1),
                        LocalDate.of(f.year(), 12, 31)));
            }
            if (f.supplierId() != null) {
                predicates.add(cb.equal(root.get("supplierId"), f.supplierId()));
            }
            if (f.supplier() != null && !f.supplier().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("supplierName")),
                        "%" + escapeLike(f.supplier().trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (f.evidenceType() != null) {
                predicates.add(cb.equal(root.get("evidenceType"), f.evidenceType()));
            }
            if (f.verification() != null) {
                predicates.add(cb.equal(root.get("verificationStatus"), f.verification()));
            }
            if (f.keyword() != null && !f.keyword().isBlank()) {
                String like = "%" + escapeLike(f.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("managementNo")), like, '\\'),
                        cb.like(cb.lower(root.get("supplierName")), like, '\\'),
                        cb.like(cb.lower(root.get("itemSummary")), like, '\\'),
                        cb.like(cb.lower(root.get("remarks")), like, '\\'),
                        cb.like(cb.lower(root.get("recipientName")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort toSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("purchaseDate"), Sort.Order.desc("managementNo"));
        }
        String[] parts = sort.split(",");
        String property = SORTABLE.get(parts[0].trim());
        if (property == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "정렬할 수 없는 항목입니다: " + parts[0]);
        }
        boolean asc = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim());
        return Sort.by(asc ? Sort.Order.asc(property) : Sort.Order.desc(property), Sort.Order.desc("managementNo"));
    }

    private static String snapshot(PurchaseLedger l) {
        return "date=" + l.getPurchaseDate() + ", supplier=" + l.getSupplierName() + ", purchase="
                + l.getPurchaseAmount() + ", payment=" + l.getPaymentAmount() + ", paymentDate=" + l.getPaymentDate()
                + ", transferRef=" + mask(l.getTransferReference()) + ", status=" + l.getVerificationStatus();
    }

    /** Keeps only the last 4 characters (account numbers must not appear in logs). */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.min(8, trimmed.length() - 4)) + trimmed.substring(trimmed.length() - 4);
    }

    public static String normalizeBusinessNo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
        }
        return value.trim();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
