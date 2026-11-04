package com.petitcamel.shop.ai.controller;

import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.domain.AiAnalysisHistory;
import com.petitcamel.shop.ai.domain.AiAnalysisStatus;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import com.petitcamel.shop.ai.domain.AiProviderType;
import com.petitcamel.shop.ai.repository.AiAnalysisHistoryRepository;
import com.petitcamel.shop.ai.service.AiAnalysisService;
import com.petitcamel.shop.ai.service.AiCostAdminService;
import com.petitcamel.shop.ai.service.AiSettingsService;
import com.petitcamel.shop.ai.service.PurchaseRecommendationService;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.security.MemberPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** AI 운영관리: availability, analysis history, usage/cost and purchase recommendations. Never exposes the key. */
@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiOperationsController {

    public record AnalysisStatus(boolean receiptEnabled, boolean productEnabled, boolean apiKeyConfigured,
                                 boolean mockMode, boolean available, String model, String provider,
                                 String mode, boolean geminiKeyConfigured, boolean localOcrEnabled,
                                 boolean openaiEnabled, boolean paidFallbackEnabled) {
    }

    public record AnalysisRow(Long id, String type, String provider, String model, String status, String errorCode,
                              String errorMessage, Integer inputTokens, Integer outputTokens,
                              BigDecimal estimatedCostUsd, Long durationMs, int attempts, int fileCount,
                              String targetType, Long targetId, Instant createdAt, String billingType) {
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
    private final AiAnalysisService analysisService;
    private final AiSettingsService settingsService;
    private final AiCostAdminService costAdminService;

    public AdminAiOperationsController(AiAnalysisProperties properties, AiAnalysisHistoryRepository historyRepository,
                                       PurchaseRecommendationService recommendationService,
                                       AiAnalysisService analysisService, AiSettingsService settingsService,
                                       AiCostAdminService costAdminService) {
        this.properties = properties;
        this.historyRepository = historyRepository;
        this.recommendationService = recommendationService;
        this.analysisService = analysisService;
        this.settingsService = settingsService;
        this.costAdminService = costAdminService;
    }

    @GetMapping("/analysis-status")
    public AnalysisStatus status() {
        AiSettingsService.Effective s = settingsService.effective();
        AiProviderType active = analysisService.activeProvider();
        boolean available = active != null || (s.localOcrAllowed() && !s.mockEnabled());
        String provider = active != null ? active.name() : s.localOcrAllowed() ? "LOCAL_OCR" : "NONE";
        String model = active == AiProviderType.GEMINI ? s.geminiModel()
                : active == AiProviderType.OPENAI ? s.openaiModel()
                : active == AiProviderType.MOCK ? "mock" : s.localOcrAllowed() ? "paddleocr+rules" : null;
        return new AnalysisStatus(properties.isReceiptEnabled(), properties.isProductEnabled(), properties.hasApiKey(),
                properties.isMockEnabled(), available, model, provider, s.mode().name(), s.geminiKeyConfigured(),
                s.localOcrEnabled(), s.openaiEnabled(), s.paidFallbackEnabled());
    }

    @GetMapping("/settings")
    public AiCostAdminService.SettingsView settings() {
        return costAdminService.settings();
    }

    @PutMapping("/settings")
    public AiCostAdminService.SettingsView updateSettings(@AuthenticationPrincipal MemberPrincipal principal,
                                                          @RequestBody AiSettingsService.Overrides request) {
        settingsService.update(request, principal.getMemberId());
        return costAdminService.settings();
    }

    @GetMapping("/cost-usage")
    public AiCostAdminService.CostUsage costUsage() {
        return costAdminService.usage();
    }

    @GetMapping("/billing")
    public List<AiCostAdminService.BillingRecord> billing(@RequestParam(defaultValue = "12") int months) {
        return costAdminService.billingRecords(Math.max(1, Math.min(months, 60)));
    }

    @PostMapping("/billing")
    public AiCostAdminService.BillingRecord addBilling(@AuthenticationPrincipal MemberPrincipal principal,
                                                       @RequestBody AiCostAdminService.BillingRequest request) {
        return costAdminService.addBilling(request, principal.getMemberId());
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
                h.getTargetType(), h.getTargetId(), h.getCreatedAt(), h.getBillingType())).toList(),
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
                "사용량은 실제 외부 AI(Gemini·OpenAI) 호출만 집계합니다(Mock·로컬 OCR·규칙 제외). 비용은 설정된 100만 토큰당 단가로 추정한 값이며 실제 청구액과 다를 수 있습니다.");
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
