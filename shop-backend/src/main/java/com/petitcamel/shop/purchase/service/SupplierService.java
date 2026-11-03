package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.purchase.domain.Supplier;
import com.petitcamel.shop.purchase.domain.SupplierProductMapping;
import com.petitcamel.shop.purchase.repository.SupplierProductMappingRepository;
import com.petitcamel.shop.purchase.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupplierService {

    public record SupplierRequest(
            @NotBlank(message = "거래처명을 입력해 주세요.") @Size(max = 200) String supplierName,
            @Size(max = 20) String businessNo,
            @Size(max = 100) String contactName,
            @Size(max = 40) String phone,
            @Size(max = 300) String address,
            @Min(0) @Max(365) Integer leadDays,
            @Size(max = 1000) String memo,
            Boolean active
    ) {
    }

    public record SupplierResponse(
            Long id,
            String supplierName,
            String businessNo,
            String contactName,
            String phone,
            String address,
            Integer leadDays,
            BigDecimal averageLeadDays,
            String memo,
            boolean active,
            long purchaseCount,
            long purchaseAmount,
            int mappedProductCount
    ) {
    }

    public record MappingResponse(
            Long id,
            String supplierProductCode,
            String supplierProductName,
            Long productId,
            Long skuId,
            String color,
            String size,
            Long lastUnitCost,
            LocalDate lastPurchaseDate
    ) {
    }

    private final SupplierRepository supplierRepository;
    private final SupplierProductMappingRepository mappingRepository;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    public SupplierService(
            SupplierRepository supplierRepository,
            SupplierProductMappingRepository mappingRepository,
            AuditLogService auditLogService,
            EntityManager entityManager) {
        this.supplierRepository = supplierRepository;
        this.mappingRepository = mappingRepository;
        this.auditLogService = auditLogService;
        this.entityManager = entityManager;
    }

    /** Finds a supplier by business number or name, creating it on first use. */
    @Transactional
    public Supplier resolveOrCreate(String name, String businessNo) {
        String trimmedName = name == null ? null : name.trim();
        if (businessNo != null && !businessNo.isBlank()) {
            var byNo = supplierRepository.findFirstByBusinessNo(businessNo.trim());
            if (byNo.isPresent()) {
                return byNo.get();
            }
        }
        if (trimmedName == null || trimmedName.isEmpty()) {
            return null;
        }
        return supplierRepository.findByNameIgnoreCase(trimmedName).orElseGet(() -> {
            Supplier supplier = new Supplier();
            supplier.setSupplierName(trimmedName);
            supplier.setBusinessNo(businessNo == null || businessNo.isBlank() ? null : businessNo.trim());
            return supplierRepository.save(supplier);
        });
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        Map<Long, long[]> totals = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT supplier_id, COUNT(*), COALESCE(SUM(purchase_amount), 0)
                FROM purchase_ledger WHERE deleted_at IS NULL AND supplier_id IS NOT NULL GROUP BY supplier_id
                """).getResultList();
        for (Object[] row : rows) {
            totals.put(((Number) row[0]).longValue(),
                    new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        Map<Long, BigDecimal> leadDays = averageLeadDays();
        return supplierRepository.findAllByOrderBySupplierNameAsc().stream()
                .map(s -> toResponse(s, totals.getOrDefault(s.getId(), new long[]{0, 0}), leadDays.get(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        Supplier supplier = require(id);
        return toResponse(supplier, new long[]{0, 0}, averageLeadDays().get(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request, Long actorId) {
        if (supplierRepository.findByNameIgnoreCase(request.supplierName()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "같은 이름의 거래처가 이미 있습니다.");
        }
        Supplier supplier = new Supplier();
        apply(supplier, request);
        supplierRepository.save(supplier);
        auditLogService.record(actorId, "SUPPLIER_CREATE", "SUPPLIER", String.valueOf(supplier.getId()),
                "name=" + supplier.getSupplierName());
        return toResponse(supplier, new long[]{0, 0}, null);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request, Long actorId) {
        Supplier supplier = require(id);
        supplierRepository.findByNameIgnoreCase(request.supplierName())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "같은 이름의 거래처가 이미 있습니다.");
                });
        apply(supplier, request);
        supplierRepository.save(supplier);
        auditLogService.record(actorId, "SUPPLIER_UPDATE", "SUPPLIER", String.valueOf(id),
                "name=" + supplier.getSupplierName());
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<MappingResponse> mappings(Long supplierId) {
        require(supplierId);
        return mappingRepository.findBySupplierIdOrderByUpdatedAtDesc(supplierId).stream()
                .map(SupplierService::toMapping).toList();
    }

    @Transactional
    public void deleteMapping(Long supplierId, Long mappingId, Long actorId) {
        SupplierProductMapping mapping = mappingRepository.findById(mappingId)
                .filter(m -> m.getSupplierId().equals(supplierId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품코드 매핑을 찾을 수 없습니다."));
        mappingRepository.delete(mapping);
        auditLogService.record(actorId, "SUPPLIER_MAPPING_DELETE", "SUPPLIER", String.valueOf(supplierId),
                "mappingId=" + mappingId);
    }

    /** Admin-entered lead days, else the average days from purchase date to receipt confirmation. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> averageLeadDays() {
        Map<Long, BigDecimal> result = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT l.supplier_id,
                       AVG(GREATEST(0, (r.confirmed_at AT TIME ZONE 'Asia/Seoul')::date - l.purchase_date))
                FROM purchase_receipt r JOIN purchase_ledger l ON l.purchase_ledger_id = r.purchase_ledger_id
                WHERE r.receipt_status = 'CONFIRMED' AND l.supplier_id IS NOT NULL
                GROUP BY l.supplier_id
                """).getResultList();
        for (Object[] row : rows) {
            BigDecimal avg = row[1] == null ? null : new BigDecimal(row[1].toString()).setScale(1, RoundingMode.HALF_UP);
            result.put(((Number) row[0]).longValue(), avg);
        }
        for (Supplier supplier : supplierRepository.findAll()) {
            if (supplier.getLeadDays() != null) {
                result.put(supplier.getId(), BigDecimal.valueOf(supplier.getLeadDays()));
            }
        }
        return result;
    }

    private Supplier require(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처를 찾을 수 없습니다."));
    }

    private static void apply(Supplier supplier, SupplierRequest request) {
        supplier.setSupplierName(request.supplierName().trim());
        supplier.setBusinessNo(PurchaseLedgerService.normalizeBusinessNo(request.businessNo()));
        supplier.setContactName(blankToNull(request.contactName()));
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setAddress(blankToNull(request.address()));
        supplier.setLeadDays(request.leadDays());
        supplier.setMemo(blankToNull(request.memo()));
        supplier.setActive(request.active() == null || request.active());
    }

    private SupplierResponse toResponse(Supplier s, long[] totals, BigDecimal averageLead) {
        int mapped = mappingRepository.findBySupplierIdOrderByUpdatedAtDesc(s.getId()).size();
        return new SupplierResponse(s.getId(), s.getSupplierName(), s.getBusinessNo(), s.getContactName(),
                s.getPhone(), s.getAddress(), s.getLeadDays(), averageLead, s.getMemo(),
                Boolean.TRUE.equals(s.getActive()), totals[0], totals[1], mapped);
    }

    private static MappingResponse toMapping(SupplierProductMapping m) {
        return new MappingResponse(m.getId(), m.getSupplierProductCode(), m.getSupplierProductName(),
                m.getProductId(), m.getSkuId(), m.getColor(), m.getSize(), m.getLastUnitCost(),
                m.getLastPurchaseDate());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
