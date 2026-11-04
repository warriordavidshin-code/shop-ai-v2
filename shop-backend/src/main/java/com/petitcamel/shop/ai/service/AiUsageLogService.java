package com.petitcamel.shop.ai.service;

import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.domain.AiAnalysisHistory;
import com.petitcamel.shop.ai.domain.AiAnalysisStatus;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import com.petitcamel.shop.ai.domain.AiProviderType;
import com.petitcamel.shop.ai.repository.AiAnalysisHistoryRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Records every AI call (tokens, estimated cost, duration, outcome) and serves the result cache. */
@Service
public class AiUsageLogService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageLogService.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiAnalysisHistoryRepository repository;
    private final AiAnalysisProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;

    public AiUsageLogService(
            AiAnalysisHistoryRepository repository,
            AiAnalysisProperties properties,
            EntityManager entityManager,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AiAnalysisHistory> findCached(AiAnalysisType type, String inputHash) {
        Instant after = clock.instant().minus(Duration.ofDays(Math.max(1, properties.getCacheDays())));
        return repository.findFirstByAnalysisTypeAndInputHashAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        type, inputHash, AiAnalysisStatus.SUCCEEDED, after)
                .filter(h -> h.getResultJson() != null);
    }

    @Transactional
    public AiAnalysisHistory recordSuccess(
            AiAnalysisType type, String inputHash, int fileCount, AiProviderType provider, String model,
            String promptVersion, String resultJson, Integer inputTokens, Integer outputTokens,
            long durationMs, int attempts, Long actorId) {
        return recordSuccess(type, inputHash, fileCount, provider, model, promptVersion, resultJson, inputTokens,
                outputTokens, durationMs, attempts, actorId,
                provider == AiProviderType.OPENAI ? estimateCost(inputTokens, outputTokens) : null,
                defaultBillingType(provider));
    }

    @Transactional
    public AiAnalysisHistory recordSuccess(
            AiAnalysisType type, String inputHash, int fileCount, AiProviderType provider, String model,
            String promptVersion, String resultJson, Integer inputTokens, Integer outputTokens,
            long durationMs, int attempts, Long actorId, BigDecimal estimatedCostUsd, String billingType) {
        AiAnalysisHistory history = base(type, inputHash, fileCount, provider, model, promptVersion, durationMs,
                attempts, actorId);
        history.setStatus(AiAnalysisStatus.SUCCEEDED);
        history.setResultJson(resultJson);
        history.setInputTokens(inputTokens);
        history.setOutputTokens(outputTokens);
        history.setEstimatedCostUsd(estimatedCostUsd);
        history.setBillingType(billingType);
        return repository.save(history);
    }

    @Transactional
    public AiAnalysisHistory recordFailure(
            AiAnalysisType type, String inputHash, int fileCount, AiProviderType provider, String model,
            String promptVersion, String errorCode, String errorMessage, long durationMs, int attempts, Long actorId) {
        return recordFailure(type, inputHash, fileCount, provider, model, promptVersion, errorCode, errorMessage,
                durationMs, attempts, actorId, defaultBillingType(provider));
    }

    @Transactional
    public AiAnalysisHistory recordFailure(
            AiAnalysisType type, String inputHash, int fileCount, AiProviderType provider, String model,
            String promptVersion, String errorCode, String errorMessage, long durationMs, int attempts, Long actorId,
            String billingType) {
        AiAnalysisHistory history = base(type, inputHash, fileCount, provider, model, promptVersion, durationMs,
                attempts, actorId);
        history.setBillingType(billingType);
        history.setStatus(AiAnalysisStatus.FAILED);
        history.setErrorCode(truncate(errorCode, 50));
        history.setErrorMessage(truncate(errorMessage, 500));
        return repository.save(history);
    }

    @Transactional
    public void linkTarget(Long analysisId, String targetType, Long targetId) {
        if (analysisId == null) {
            return;
        }
        repository.findById(analysisId).ifPresent(h -> {
            h.setTargetType(targetType);
            h.setTargetId(targetId);
        });
    }

    public BigDecimal estimateCost(Integer inputTokens, Integer outputTokens) {
        return estimateCost(inputTokens, outputTokens, properties.getInputPricePerMillion(),
                properties.getOutputPricePerMillion());
    }

    public static BigDecimal estimateCost(Integer inputTokens, Integer outputTokens, BigDecimal inputPrice,
                                          BigDecimal outputPrice) {
        if (inputTokens == null && outputTokens == null) {
            return null;
        }
        BigDecimal in = BigDecimal.valueOf(inputTokens == null ? 0 : inputTokens).multiply(inputPrice);
        BigDecimal out = BigDecimal.valueOf(outputTokens == null ? 0 : outputTokens).multiply(outputPrice);
        return in.add(out).divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    private static String defaultBillingType(AiProviderType provider) {
        return switch (provider) {
            case OPENAI -> "PAID";
            case GEMINI -> "FREE";
            case LOCAL_OCR -> "LOCAL";
            case MOCK -> "NONE";
        };
    }

    @Transactional(readOnly = true)
    public AiAnalysisHistoryRepository.UsageSummary usageSince(Instant from) {
        return repository.usageSince(from);
    }

    /**
     * Cached results can contain names and transfer details from receipts; drop them once the cache window
     * has passed. Usage metadata (tokens, cost, status) is kept.
     */
    @Scheduled(cron = "${app.ai.analysis.purge-cron:0 30 4 * * *}")
    @Transactional
    public void purgeExpiredResults() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(Math.max(1, properties.getCacheDays())));
        int cleared = entityManager.createQuery(
                        "update AiAnalysisHistory h set h.resultJson = null "
                                + "where h.createdAt < :cutoff and h.resultJson is not null")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
        if (cleared > 0) {
            log.info("Cleared {} expired AI analysis results", cleared);
        }
    }

    private AiAnalysisHistory base(
            AiAnalysisType type, String inputHash, int fileCount, AiProviderType provider, String model,
            String promptVersion, long durationMs, int attempts, Long actorId) {
        AiAnalysisHistory history = new AiAnalysisHistory();
        history.setAnalysisType(type);
        history.setInputHash(inputHash);
        history.setInputFileCount(fileCount);
        history.setProvider(provider);
        history.setModelName(truncate(model, 100));
        history.setPromptVersion(promptVersion);
        history.setDurationMs(durationMs);
        history.setAttemptCount(Math.max(1, attempts));
        history.setRequestedBy(actorId);
        history.setCreatedAt(clock.instant());
        return history;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
