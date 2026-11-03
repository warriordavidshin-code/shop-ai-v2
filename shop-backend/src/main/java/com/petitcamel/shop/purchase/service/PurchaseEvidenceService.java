package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.common.storage.InspectedFile;
import com.petitcamel.shop.common.storage.UploadedFileInspector;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceCategory;
import com.petitcamel.shop.purchase.domain.PurchaseEvidence;
import com.petitcamel.shop.purchase.domain.PurchaseLedger;
import com.petitcamel.shop.purchase.dto.PurchaseDtos;
import com.petitcamel.shop.purchase.repository.PurchaseEvidenceRepository;
import com.petitcamel.shop.purchase.repository.PurchaseLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase evidence files (receipts, statements, transfer records). Files live under {@code app.upload.private-dir},
 * which is NOT mapped to {@code /uploads/**}; they are streamed only through the admin API.
 */
@Service
public class PurchaseEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseEvidenceService.class);
    private static final int MAX_FILES_PER_UPLOAD = 10;

    private final PurchaseEvidenceRepository evidenceRepository;
    private final PurchaseLedgerRepository ledgerRepository;
    private final AuditLogService auditLogService;
    private final AiAnalysisProperties aiProperties;
    private final Clock clock;
    private final Path privateRoot;

    public PurchaseEvidenceService(
            PurchaseEvidenceRepository evidenceRepository,
            PurchaseLedgerRepository ledgerRepository,
            AuditLogService auditLogService,
            AiAnalysisProperties aiProperties,
            Clock clock,
            @Value("${app.upload.private-dir:../private-uploads}") String privateDir) {
        this.evidenceRepository = evidenceRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditLogService = auditLogService;
        this.aiProperties = aiProperties;
        this.clock = clock;
        this.privateRoot = Path.of(privateDir).toAbsolutePath().normalize();
    }

    public List<InspectedFile> inspectAll(List<MultipartFile> files, int maxFiles) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "업로드할 파일을 선택해 주세요.");
        }
        if (files.size() > maxFiles) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "파일은 한 번에 최대 " + maxFiles + "개까지 업로드할 수 있습니다.");
        }
        List<InspectedFile> inspected = new ArrayList<>();
        for (MultipartFile file : files) {
            inspected.add(UploadedFileInspector.inspect(file, UploadedFileInspector.IMAGE_AND_PDF_TYPES,
                    aiProperties.getMaxFileBytes()));
        }
        return inspected;
    }

    /** Validates and stores uploads; optionally attaches them to an existing ledger entry. */
    @Transactional
    public List<PurchaseDtos.EvidenceResponse> upload(List<MultipartFile> files, Long ledgerId,
                                                      EvidenceCategory category, Long actorId) {
        PurchaseLedger ledger = ledgerId == null ? null : ledgerRepository.findByIdAndDeletedAtIsNull(ledgerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매입내역을 찾을 수 없습니다."));
        List<PurchaseEvidence> saved = new ArrayList<>();
        for (InspectedFile file : inspectAll(files, MAX_FILES_PER_UPLOAD)) {
            PurchaseEvidence evidence = store(file, actorId);
            if (category != null) {
                evidence.setEvidenceCategory(category);
            }
            if (ledger != null) {
                attach(evidence, ledger.getId());
            }
            saved.add(evidenceRepository.save(evidence));
        }
        if (ledger != null) {
            auditLogService.record(actorId, "PURCHASE_EVIDENCE_ATTACH", "PURCHASE_LEDGER", String.valueOf(ledger.getId()),
                    "files=" + saved.size());
        }
        return toResponses(saved);
    }

    /** Stores a file, reusing an identical unattached upload (same SHA-256) instead of writing it again. */
    @Transactional
    public PurchaseEvidence store(InspectedFile file, Long actorId) {
        var reusable = evidenceRepository.findFirstByFileHashAndPurchaseLedgerIdIsNullOrderByIdDesc(file.sha256());
        if (reusable.isPresent() && Files.exists(resolve(reusable.get()))) {
            return reusable.get();
        }
        String datePath = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))
                .format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String relative = "purchase-evidence/" + datePath + "/" + UUID.randomUUID().toString().replace("-", "")
                + file.extension();
        Path target = privateRoot.resolve(relative).normalize();
        if (!target.startsWith(privateRoot)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일 저장 경로가 올바르지 않습니다.");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, file.data());
        } catch (IOException ex) {
            log.error("Failed to store purchase evidence", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "증빙 파일 저장에 실패했습니다.");
        }
        PurchaseEvidence evidence = new PurchaseEvidence();
        evidence.setOriginalFileName(file.originalFileName());
        evidence.setStoredPath(relative);
        evidence.setContentType(file.contentType());
        evidence.setFileSize(file.size());
        evidence.setFileHash(file.sha256());
        evidence.setEvidenceCategory(file.contentType().equals("application/pdf")
                ? EvidenceCategory.STATEMENT : EvidenceCategory.OTHER);
        evidence.setUploadedBy(actorId);
        return evidenceRepository.save(evidence);
    }

    /** Links evidence to a ledger; the same file cannot be attached twice to one entry. */
    @Transactional
    public void attach(PurchaseEvidence evidence, Long ledgerId) {
        if (ledgerId.equals(evidence.getPurchaseLedgerId())) {
            return;
        }
        if (evidence.getPurchaseLedgerId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    evidence.getOriginalFileName() + ": 이미 다른 매입내역에 연결된 증빙입니다.");
        }
        boolean sameFileAttached = evidenceRepository.findByPurchaseLedgerIdOrderByIdAsc(ledgerId).stream()
                .anyMatch(e -> e.getFileHash().equals(evidence.getFileHash()));
        if (sameFileAttached) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    evidence.getOriginalFileName() + ": 같은 파일이 이미 이 매입내역에 첨부되어 있습니다.");
        }
        evidence.setPurchaseLedgerId(ledgerId);
    }

    @Transactional
    public void attachAll(Collection<Long> evidenceIds, Long ledgerId) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return;
        }
        for (Long id : evidenceIds) {
            PurchaseEvidence evidence = require(id);
            attach(evidence, ledgerId);
            evidenceRepository.save(evidence);
        }
    }

    @Transactional(readOnly = true)
    public PurchaseEvidence require(Long id) {
        return evidenceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "증빙 파일을 찾을 수 없습니다."));
    }

    public Path resolve(PurchaseEvidence evidence) {
        Path path = privateRoot.resolve(evidence.getStoredPath()).normalize();
        if (!path.startsWith(privateRoot)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "허용되지 않은 파일 경로입니다.");
        }
        return path;
    }

    public byte[] read(PurchaseEvidence evidence) {
        try {
            return Files.readAllBytes(resolve(evidence));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "증빙 파일 원본을 찾을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseDtos.EvidenceResponse> list(String linked, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
        Page<PurchaseEvidence> result = switch (linked == null ? "all" : linked) {
            case "unlinked" -> evidenceRepository.findByPurchaseLedgerIdIsNullOrderByIdDesc(pageable);
            case "linked" -> evidenceRepository.findByPurchaseLedgerIdIsNotNullOrderByIdDesc(pageable);
            default -> evidenceRepository.findAllByOrderByIdDesc(pageable);
        };
        return PageResponse.of(toResponses(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }

    @Transactional
    public PurchaseDtos.EvidenceResponse updateCategory(Long id, EvidenceCategory category, Long actorId) {
        PurchaseEvidence evidence = require(id);
        evidence.setEvidenceCategory(category);
        evidenceRepository.save(evidence);
        auditLogService.record(actorId, "PURCHASE_EVIDENCE_CATEGORY", "PURCHASE_EVIDENCE", String.valueOf(id),
                "category=" + category);
        return toResponses(List.of(evidence)).get(0);
    }

    @Transactional
    public void delete(Long id, Long actorId) {
        PurchaseEvidence evidence = require(id);
        Path path = resolve(evidence);
        evidenceRepository.delete(evidence);
        auditLogService.record(actorId, "PURCHASE_EVIDENCE_DELETE", "PURCHASE_EVIDENCE", String.valueOf(id),
                "ledgerId=" + evidence.getPurchaseLedgerId() + ", file=" + evidence.getOriginalFileName());
        deleteQuietly(path);
    }

    /** Uploads that were never saved to a ledger entry are removed after 7 days. */
    @Scheduled(cron = "${app.purchase.evidence-cleanup-cron:0 10 4 * * *}")
    @Transactional
    public void purgeUnlinked() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(7));
        List<PurchaseEvidence> stale = evidenceRepository.findByPurchaseLedgerIdIsNullAndCreatedAtBefore(cutoff);
        for (PurchaseEvidence evidence : stale) {
            Path path = resolve(evidence);
            evidenceRepository.delete(evidence);
            deleteQuietly(path);
        }
        if (!stale.isEmpty()) {
            log.info("Removed {} unlinked purchase evidence files older than 7 days", stale.size());
        }
    }

    @Transactional(readOnly = true)
    public List<PurchaseDtos.EvidenceResponse> toResponses(List<PurchaseEvidence> evidences) {
        List<Long> ledgerIds = evidences.stream().map(PurchaseEvidence::getPurchaseLedgerId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, PurchaseLedger> ledgers = ledgerIds.isEmpty() ? Map.of()
                : ledgerRepository.findAllById(ledgerIds).stream()
                .collect(Collectors.toMap(PurchaseLedger::getId, Function.identity()));
        return evidences.stream().map(e -> {
            PurchaseLedger ledger = e.getPurchaseLedgerId() == null ? null : ledgers.get(e.getPurchaseLedgerId());
            return new PurchaseDtos.EvidenceResponse(e.getId(), e.getPurchaseLedgerId(),
                    ledger == null ? null : ledger.getManagementNo(), e.getOriginalFileName(), e.getContentType(),
                    e.getFileSize(), e.getFileHash(), e.getEvidenceCategory(), e.getEvidenceCategory().label(),
                    e.getAiAnalysisStatus(), e.getCreatedAt(), "/api/admin/purchases/evidence/" + e.getId() + "/file");
        }).toList();
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete evidence file {}", path.getFileName());
        }
    }
}
