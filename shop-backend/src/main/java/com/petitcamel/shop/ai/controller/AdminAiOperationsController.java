package com.petitcamel.shop.ai.controller;

import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.domain.AiAnalysisHistory;
import com.petitcamel.shop.ai.domain.AiAnalysisStatus;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import com.petitcamel.shop.ai.repository.AiAnalysisHistoryRepository;
import com.petitcamel.shop.ai.service.PurchaseRecommendationService;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.security.MemberPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** AI 운영관리: availability, analysis history, usage/cost and purchase recommendations. Never exposes the key. */
@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiOperationsController {

    public record AnalysisStatus(boolean receiptEnabled, boolean productEnabled, boolean apiKeyConfigured,
                                 boolean mockMode, boolean available, String model, String provider) {
    }

    public record AnalysisRow(Long id, String type, String provider, String model, String status, String errorCode,
                              String errorMessage, Integer inputTokens, Integer outputTokens,
                              BigDecimal estimatedCostUsd, Long durationMs, int attempts, int fileCount,
                              String targetType, Long targetId, Instant createdAt) {
    }

    public record Usage(LocalDate from, long calls, long failures, long inputTokens, long outputTokens,
                        BigDecimal estimatedCostUsd, String note) {
    }

    public record DecisionRequest(String decision) {
    }

    public record GenerateRequest(Boolean useAi) {
    }

    private final AiAnalysisProperties properties;
    private final AiAnalysisHistoryRepository historyRepository;
    private final PurchaseRecommendationService recommendationService;

    public AdminAiOperationsController(AiAnalysisProperties properties, AiAnalysisHistoryRepository historyRepository,
                                       PurchaseRecommendationService recommendationService) {
        this.properties = properties;
        this.historyRepository = historyRepository;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/analysis-status")
    public AnalysisStatus status() {
        return new AnalysisStatus(properties.isReceiptEnabled(), properties.isProductEnabled(), properties.hasApiKey(),
                properties.isMockEnabled(), properties.isAvailable(),
                properties.isMockEnabled() ? "mock" : properties.effectiveModel(),
                properties.isMockEnabled() ? "MOCK" : "OPENAI");
    }

    @GetMapping("/analyses")
    public PageResponse<AnalysisRow> analyses(@RequestParam(required = false) AiAnalysisType type,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 30 : Math.min(size, 100));
        Page<AiAnalysisHistory> result = type == null ? historyRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : historyRepository.findByAnalysisTypeOrderByCreatedAtDescIdDesc(type, pageable);
        return PageResponse.of(result.getContent().stream().map(h -> new AnalysisRow(h.getId(),
                h.getAnalysisType().name(), h.getProvider().name(), h.getModelName(), h.getStatus().name(),
                h.getErrorCode(), h.getErrorMessage(), h.getInputTokens(), h.getOutputTokens(),
                h.getEstimatedCostUsd(), h.getDurationMs(), h.getAttemptCount(), h.getInputFileCount(),
                h.getTargetType(), h.getTargetId(), h.getCreatedAt())).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @GetMapping("/usage")
    public Usage usage(@RequestParam(defaultValue = "30") int days) {
        int span = Math.max(1, Math.min(days, 366));
        LocalDate from = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(span - 1L);
        Instant since = from.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        var usage = historyRepository.usageSince(since);
        long failures = historyRepository.countByCreatedAtAfterAndStatusAndProvider(since, AiAnalysisStatus.FAILED,
                com.petitcamel.shop.ai.domain.AiProviderType.OPENAI);
        return new Usage(from, num(usage.getCalls()), failures, num(usage.getInputTokens()),
                num(usage.getOutputTokens()), usage.getEstimatedCostUsd() == null ? BigDecimal.ZERO
                : new BigDecimal(usage.getEstimatedCostUsd().toString()),
                "사용량은 실제 OpenAI 호출만 집계합니다(Mock·규칙 기반 호출 제외). 비용은 설정된 100만 토큰당 단가로 추정한 값이며 실제 청구액과 다를 수 있습니다.");
    }

    @PostMapping("/purchase-recommendations")
    public PurchaseRecommendationService.GenerateResponse generate(@AuthenticationPrincipal MemberPrincipal principal,
                                                                   @RequestBody(required = false) GenerateRequest request) {
        boolean useAi = request == null || request.useAi() == null || request.useAi();
        return recommendationService.generate(useAi, principal.getMemberId());
    }

    @GetMapping("/purchase-recommendations")
    public PageResponse<PurchaseRecommendationService.RecommendationResponse> recommendations(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return recommendationService.list(status, page, size);
    }

    @PostMapping("/purchase-recommendations/{id}/decision")
    public PurchaseRecommendationService.RecommendationResponse decide(@AuthenticationPrincipal MemberPrincipal principal,
                                                                       @PathVariable Long id,
                                                                       @RequestBody DecisionRequest request) {
        return recommendationService.decide(id, request == null ? null : request.decision(), principal.getMemberId());
    }

    private static long num(Number n) {
        return n == null ? 0 : n.longValue();
    }
}
