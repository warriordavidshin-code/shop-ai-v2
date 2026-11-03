package com.petitcamel.shop.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.ai.client.AiClientException;
import com.petitcamel.shop.ai.client.AiInputFile;
import com.petitcamel.shop.ai.client.AiStructuredRequest;
import com.petitcamel.shop.ai.client.AiStructuredResult;
import com.petitcamel.shop.ai.client.OpenAiResponsesClient;
import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.domain.AiAnalysisHistory;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import com.petitcamel.shop.ai.domain.AiProviderType;
import com.petitcamel.shop.ai.support.JsonSchemaValidator;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.storage.UploadedFileInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * Common pipeline for AI analyses: availability check, result cache, OpenAI (or mock) call,
 * server-side schema validation, and usage logging. Callers add domain validation on top.
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiAnalysisProperties properties;
    private final OpenAiResponsesClient client;
    private final AiUsageLogService usageLogService;
    private final ObjectMapper objectMapper;

    public AiAnalysisService(
            AiAnalysisProperties properties,
            OpenAiResponsesClient client,
            AiUsageLogService usageLogService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.usageLogService = usageLogService;
        this.objectMapper = objectMapper;
    }

    public record Spec(
            AiAnalysisType type,
            String promptVersion,
            AiStructuredRequest request,
            /* hash of everything that determines the answer (files, prompt inputs) */
            String contentHash,
            Supplier<JsonNode> mockOutput
    ) {
    }

    public record Outcome(
            Long analysisId,
            JsonNode output,
            AiProviderType provider,
            String model,
            boolean cached,
            Integer inputTokens,
            Integer outputTokens,
            BigDecimal estimatedCostUsd,
            int attempts
    ) {
        public boolean mock() {
            return provider == AiProviderType.MOCK;
        }
    }

    public AiProviderType activeProvider() {
        return properties.isMockEnabled() ? AiProviderType.MOCK : AiProviderType.OPENAI;
    }

    public void ensureAvailable(boolean featureEnabled, String featureName) {
        if (!featureEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    featureName + " 기능이 비활성화되어 있습니다. 수동 입력은 계속 사용할 수 있습니다.");
        }
        if (!properties.isAvailable()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "OpenAI API 키가 설정되지 않아 " + featureName + " 기능을 사용할 수 없습니다. "
                            + "수동 입력은 계속 사용할 수 있습니다.");
        }
    }

    public Outcome run(Spec spec, Long actorId, boolean forceRefresh) {
        AiProviderType provider = activeProvider();
        String model = provider == AiProviderType.MOCK ? "mock" : properties.effectiveModel();
        String inputHash = UploadedFileInspector.sha256(
                spec.type() + "|" + spec.promptVersion() + "|" + provider + "|" + model + "|" + spec.contentHash());
        List<AiInputFile> files = spec.request().files();

        if (!forceRefresh) {
            var cached = usageLogService.findCached(spec.type(), inputHash);
            if (cached.isPresent()) {
                AiAnalysisHistory history = cached.get();
                try {
                    JsonNode output = objectMapper.readTree(history.getResultJson());
                    return new Outcome(history.getId(), output, history.getProvider(), history.getModelName(), true,
                            history.getInputTokens(), history.getOutputTokens(), history.getEstimatedCostUsd(),
                            history.getAttemptCount());
                } catch (JsonProcessingException ex) {
                    log.warn("Ignoring unreadable cached AI result id={}", history.getId());
                }
            }
        }

        long started = System.currentTimeMillis();
        JsonNode output;
        Integer inTokens = null;
        Integer outTokens = null;
        int attempts = 1;
        String usedModel = model;
        try {
            if (provider == AiProviderType.MOCK) {
                output = spec.mockOutput().get();
            } else {
                AiStructuredResult result = client.createStructured(spec.request());
                output = result.output();
                inTokens = result.inputTokens();
                outTokens = result.outputTokens();
                attempts = result.attempts();
                usedModel = result.model();
            }
        } catch (AiClientException ex) {
            usageLogService.recordFailure(spec.type(), inputHash, files.size(), provider, model,
                    spec.promptVersion(), ex.getType().name(), ex.getMessage(),
                    System.currentTimeMillis() - started, ex.getAttempts(), actorId);
            throw toBusiness(ex);
        }

        List<String> schemaErrors = JsonSchemaValidator.validate(spec.request().schema(), output);
        if (!schemaErrors.isEmpty()) {
            log.warn("AI output failed schema validation type={} errors={}", spec.type(),
                    schemaErrors.subList(0, Math.min(5, schemaErrors.size())));
            usageLogService.recordFailure(spec.type(), inputHash, files.size(), provider, usedModel,
                    spec.promptVersion(), "SCHEMA_VALIDATION", String.join("; ", schemaErrors),
                    System.currentTimeMillis() - started, attempts, actorId);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "AI 결과가 형식 검증을 통과하지 못했습니다. 다시 시도해 주세요.");
        }

        AiAnalysisHistory saved = usageLogService.recordSuccess(spec.type(), inputHash, files.size(), provider,
                usedModel, spec.promptVersion(), output.toString(), inTokens, outTokens,
                System.currentTimeMillis() - started, attempts, actorId);
        return new Outcome(saved.getId(), output, provider, usedModel, false, inTokens, outTokens,
                saved.getEstimatedCostUsd(), attempts);
    }

    private static BusinessException toBusiness(AiClientException ex) {
        ErrorCode code = switch (ex.getType()) {
            case NOT_CONFIGURED -> ErrorCode.SERVICE_UNAVAILABLE;
            case RATE_LIMITED -> ErrorCode.TOO_MANY_REQUESTS;
            default -> ErrorCode.EXTERNAL_SERVICE_ERROR;
        };
        return new BusinessException(code, ex.getMessage());
    }
}
