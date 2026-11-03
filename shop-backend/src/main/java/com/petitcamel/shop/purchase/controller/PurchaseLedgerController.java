package com.petitcamel.shop.purchase.controller;

import com.petitcamel.shop.security.MemberPrincipal;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceCategory;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceType;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.VerificationStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEvidence;
import com.petitcamel.shop.purchase.dto.PurchaseDtos;
import com.petitcamel.shop.purchase.service.PurchaseEvidenceService;
import com.petitcamel.shop.purchase.service.PurchaseExcelExportService;
import com.petitcamel.shop.purchase.service.PurchaseItemMatchingService;
import com.petitcamel.shop.purchase.service.PurchaseLedgerService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/purchases")
public class PurchaseLedgerController {

    private final PurchaseLedgerService ledgerService;
    private final PurchaseEvidenceService evidenceService;
    private final PurchaseItemMatchingService matchingService;
    private final PurchaseExcelExportService exportService;

    public PurchaseLedgerController(PurchaseLedgerService ledgerService, PurchaseEvidenceService evidenceService,
                                    PurchaseItemMatchingService matchingService,
                                    PurchaseExcelExportService exportService) {
        this.ledgerService = ledgerService;
        this.evidenceService = evidenceService;
        this.matchingService = matchingService;
        this.exportService = exportService;
    }

    @GetMapping
    public PageResponse<PurchaseDtos.LedgerRow> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) EvidenceType evidenceType,
            @RequestParam(required = false) VerificationStatus verification,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ledgerService.list(new PurchaseLedgerService.LedgerFilter(from, to, month, year, supplier, supplierId,
                evidenceType, verification, keyword), page, size, sort);
    }

    @GetMapping("/{id:\\d+}")
    public PurchaseDtos.LedgerDetail get(@PathVariable Long id) {
        return ledgerService.get(id);
    }

    @PostMapping
    public PurchaseDtos.LedgerDetail create(@AuthenticationPrincipal MemberPrincipal principal,
                                            @Valid @RequestBody PurchaseDtos.LedgerRequest request) {
        return ledgerService.create(request, principal.getMemberId());
    }

    @PutMapping("/{id:\\d+}")
    public PurchaseDtos.LedgerDetail update(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id,
                                            @Valid @RequestBody PurchaseDtos.LedgerRequest request) {
        return ledgerService.update(id, request, principal.getMemberId());
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        ledgerService.delete(id, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public PurchaseDtos.SummaryResponse summary(@RequestParam(required = false) Integer year) {
        return ledgerService.summary(year == null ? LocalDate.now().getYear() : year);
    }

    /**
     * scope: all | year | month | supplier | search (current list filters). The file keeps the template layout.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "search") String scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) EvidenceType evidenceType,
            @RequestParam(required = false) VerificationStatus verification,
            @RequestParam(required = false) String keyword) {
        PurchaseLedgerService.LedgerFilter filter;
        String fileName;
        Integer summaryYear = null;
        switch (scope) {
            case "all" -> {
                filter = PurchaseLedgerService.LedgerFilter.empty();
                fileName = "상품매입장부_전체.xlsx";
            }
            case "year" -> {
                int y = year == null ? LocalDate.now().getYear() : year;
                filter = new PurchaseLedgerService.LedgerFilter(null, null, null, y, null, null, null, null, null);
                fileName = "상품매입장부_" + y + "년.xlsx";
                summaryYear = y;
            }
            case "month" -> {
                if (month == null || !month.matches("\\d{4}-\\d{2}")) {
                    throw new com.petitcamel.shop.common.exception.BusinessException(
                            com.petitcamel.shop.common.exception.ErrorCode.VALIDATION_FAILED,
                            "month 는 YYYY-MM 형식이어야 합니다.");
                }
                filter = new PurchaseLedgerService.LedgerFilter(null, null, month, null, null, null, null, null, null);
                fileName = "상품매입장부_" + month.substring(0, 4) + "년_" + month.substring(5) + "월.xlsx";
                summaryYear = Integer.parseInt(month.substring(0, 4));
            }
            case "supplier" -> {
                if ((supplier == null || supplier.isBlank()) && supplierId == null) {
                    throw new com.petitcamel.shop.common.exception.BusinessException(
                            com.petitcamel.shop.common.exception.ErrorCode.VALIDATION_FAILED, "거래처를 선택해 주세요.");
                }
                filter = new PurchaseLedgerService.LedgerFilter(from, to, null, year, supplier, supplierId, null, null,
                        null);
                String label = supplier == null || supplier.isBlank() ? "거래처" + supplierId : supplier.trim();
                fileName = "상품매입장부_" + label.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_") + ".xlsx";
                summaryYear = year;
            }
            default -> {
                filter = new PurchaseLedgerService.LedgerFilter(from, to, month, year, supplier, supplierId,
                        evidenceType, verification, keyword);
                fileName = "상품매입장부_검색결과_" + LocalDate.now().toString().replace("-", "") + ".xlsx";
                summaryYear = year;
            }
        }
        PurchaseExcelExportService.Export export = exportService.export(filter, fileName, summaryYear);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Export-Rows", String.valueOf(export.rowCount()))
                .cacheControl(CacheControl.noStore())
                .body(export.content());
    }

    @PostMapping("/duplicates-check")
    public List<PurchaseDtos.DuplicateCandidate> duplicates(@RequestBody PurchaseDtos.DuplicateCheckRequest request) {
        return ledgerService.findDuplicates(request);
    }

    @PostMapping(value = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<PurchaseDtos.EvidenceResponse> uploadEvidence(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) Long ledgerId,
            @RequestParam(required = false) EvidenceCategory category) {
        return evidenceService.upload(files, ledgerId, category, principal.getMemberId());
    }

    @GetMapping("/evidence")
    public PageResponse<PurchaseDtos.EvidenceResponse> listEvidence(
            @RequestParam(required = false) String linked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return evidenceService.list(linked, page, size);
    }

    @GetMapping("/evidence/{id}/file")
    public ResponseEntity<byte[]> evidenceFile(@PathVariable Long id,
                                               @RequestParam(defaultValue = "false") boolean download) {
        PurchaseEvidence evidence = evidenceService.require(id);
        byte[] data = evidenceService.read(evidence);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(evidence.getOriginalFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(evidence.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")
                .cacheControl(CacheControl.noStore())
                .body(data);
    }

    @PatchMapping("/evidence/{id}")
    public PurchaseDtos.EvidenceResponse updateEvidence(@AuthenticationPrincipal MemberPrincipal principal,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody PurchaseDtos.EvidenceCategoryRequest request) {
        return evidenceService.updateCategory(id, request.category(), principal.getMemberId());
    }

    @DeleteMapping("/evidence/{id}")
    public ResponseEntity<Void> deleteEvidence(@AuthenticationPrincipal MemberPrincipal principal,
                                               @PathVariable Long id) {
        evidenceService.delete(id, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items/match")
    public List<PurchaseItemMatchingService.UnmatchedItem> match(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody PurchaseItemMatchingService.MatchRequest request) {
        return matchingService.match(request, principal.getMemberId());
    }

    @GetMapping("/items/unmatched")
    public PageResponse<PurchaseItemMatchingService.UnmatchedItem> unmatched(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return matchingService.unmatched(page, size);
    }

    @GetMapping("/items/{itemId}/candidates")
    public List<PurchaseItemMatchingService.Candidate> candidates(@PathVariable Long itemId) {
        return matchingService.candidates(itemId);
    }
}
