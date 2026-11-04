package com.petitcamel.shop.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.ai.client.AiClientException;
import com.petitcamel.shop.ai.client.AiInputFile;
import com.petitcamel.shop.ai.client.AiStructuredRequest;
import com.petitcamel.shop.ai.client.AiStructuredResult;
import com.petitcamel.shop.ai.client.GeminiClient;
import com.petitcamel.shop.ai.client.OpenAiResponsesClient;
import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.config.AiCostProperties;
import com.petitcamel.shop.ai.domain.AiAnalysisHistory;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import com.petitcamel.shop.ai.domain.AiMode;
import com.petitcamel.shop.ai.domain.AiProviderType;
import com.petitcamel.shop.ai.support.JsonSchemaNormalizer;
import com.petitcamel.shop.ai.support.JsonSchemaValidator;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.storage.UploadedFileInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Common pipeline for external AI analyses with free-first routing:
 * <ol>
 *   <li>result cache (file SHA-256 + prompt version + provider/model) across every provider;</li>
 *   <li>Gemini while the free quota lasts;</li>
 *   <li>OpenAI only when the admin enabled it and either turned on automatic paid fallback or approved this
 *       request ({@code allowPaid});</li>
 *   <li>otherwise a clear error so the admin can enter data manually.</li>
 * </ol>
 * Every external call takes a slot of the daily limit and paid calls reserve their worst-case cost against the
 * monthly budget first (both atomic). Local OCR for receipts runs before this pipeline in the caller.
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);
    private static final int TOKENS_PER_FILE_ESTIMATE = 2500;

    private final AiAnalysisProperties properties;
    private final AiCostProperties costProperties;
    private final OpenAiResponsesClient openAiClient;
    private final GeminiClient geminiClient;
    private final AiUsageLogService usageLogService;
    private final AiSettingsService settingsService;
    private final AiQuotaService quotaService;
    private final ObjectMapper objectMapper;

    public AiAnalysisService(
            AiAnalysisProperties properties,
            AiCostProperties costProperties,
            OpenAiResponsesClient openAiClient,
            GeminiClient geminiClient,
            AiUsageLogService usageLogService,
            AiSettingsService settingsService,
            AiQuotaService quotaService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.costProperties = costProperties;
        this.openAiClient = openAiClient;
        this.geminiClient = geminiClient;
        this.usageLogService = usageLogService;
        this.settingsService = settingsService;
        this.quotaService = quotaService;
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

    public record Options(boolean forceRefresh, boolean allowPaid) {
        public static Options defaults() {
            return new Options(false, false);
        }
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
            int attempts,
            String billingType,
            List<String> notices
    ) {
        public boolean mock() {
            return provider == AiProviderType.MOCK;
        }

        public boolean paid() {
            return "PAID".equals(billingType);
        }
    }

    /** One step of the provider chain. */
    record Route(AiProviderType provider, String model, boolean paid) {
    }

    /** Provider chain for one request plus what the admin could still unlock. */
    record Plan(List<Route> routes, boolean paidApprovalAvailable, AiSettingsService.Effective settings) {
    }

    /** Provider label for status screens: what would serve the next external request. */
    public AiProviderType activeProvider() {
        Plan plan = plan(settingsService.effective(), false);
        return plan.routes().isEmpty() ? null : plan.routes().get(0).provider();
    }

    public void ensureAvailable(boolean featureEnabled, String featureName) {
        if (!featureEnabled) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    featureName + " 기능이 비활성화되어 있습니다. 수동 입력은 계속 사용할 수 있습니다.");
        }
        if (settingsService.effective().mode() == AiMode.MANUAL) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "AI가 수동 입력 모드로 설정되어 있습니다. 관리자 > AI 환경설정에서 변경할 수 있으며, "
                            + "수동 입력은 계속 사용할 수 있습니다.");
        }
    }

    /** True when at least one external provider could serve a request right now (ignoring quotas). */
    public boolean externalAvailable(boolean allowPaid) {
        return !plan(settingsService.effective(), allowPaid).routes().isEmpty();
    }

    public Outcome run(Spec spec, Long actorId, boolean forceRefresh) {
        return run(spec, actorId, new Options(forceRefresh, false));
    }

    public Outcome run(Spec spec, Long actorId, Options options) {
        AiSettingsService.Effective settings = settingsService.effective();
        if (settings.cacheEnabled() && !options.forceRefresh()) {
            Optional<Outcome> cached = findCached(spec, settings);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        Plan plan = plan(settings, options.allowPaid());
        if (plan.routes().isEmpty()) {
            throw unavailable(plan, List.of(), false);
        }

        List<String> notices = new ArrayList<>();
        AiClientException lastError = null;
        boolean budgetBlocked = false;
        boolean freeQuotaExhausted = false;
        List<AiInputFile> files = spec.request().files();
        for (Route route : plan.routes()) {
            String inputHash = inputHash(spec, route.provider(), route.model());
            BigDecimal reserved = null;
            if (route.provider() != AiProviderType.MOCK) {
                if (route.provider() == AiProviderType.GEMINI && !route.paid()
                        && quotaService.isQuotaExhaustedToday(AiProviderType.GEMINI)) {
                    freeQuotaExhausted = true;
                    notices.add("Gemini 무료 사용 한도가 오늘 모두 소진되어 건너뛰었습니다.");
                    continue;
                }
                if (!quotaService.tryAcquireDaily(settings.dailyRequestLimit())) {
                    quotaService.recordBlocked(route.provider());
                    throw new BusinessException(ErrorCode.AI_LIMIT_EXCEEDED,
                            "오늘 외부 AI 요청 한도(" + settings.dailyRequestLimit() + "회)를 모두 사용했습니다. "
                                    + "내일 다시 시도하거나 관리자 > AI 환경설정에서 한도를 조정해 주세요. "
                                    + "수동 입력은 계속 사용할 수 있습니다.");
                }
                if (route.paid()) {
                    reserved = maxCost(route, spec.request());
                    if (!quotaService.tryReserveBudget(reserved, settings.monthlyBudgetUsd())) {
                        quotaService.releaseDaily();
                        quotaService.recordBlocked(route.provider());
                        budgetBlocked = true;
                        notices.add("이번 달 AI 예산($" + settings.monthlyBudgetUsd().stripTrailingZeros().toPlainString()
                                + ")을 초과하게 되어 " + route.provider() + " 유료 호출을 차단했습니다.");
                        continue;
                    }
                }
            }

            long started = System.currentTimeMillis();
            AiStructuredResult result;
            try {
                result = call(route, spec);
            } catch (AiClientException ex) {
                long duration = System.currentTimeMillis() - started;
                usageLogService.recordFailure(spec.type(), inputHash, files.size(), route.provider(), route.model(),
                        spec.promptVersion(), ex.getType().name(), ex.getMessage(), duration, ex.getAttempts(),
                        actorId, billingType(route));
                if (route.provider() != AiProviderType.MOCK) {
                    quotaService.recordAttempt(route.provider(), false, null, null, null);
                }
                if (reserved != null) {
                    quotaService.settleBudget(reserved, BigDecimal.ZERO);
                }
                if (route.provider() == AiProviderType.GEMINI && ex.getType() == AiClientException.Type.RATE_LIMITED) {
                    quotaService.markQuotaExhausted(AiProviderType.GEMINI);
                    freeQuotaExhausted = true;
                    notices.add("Gemini 무료 사용 한도를 초과했습니다.");
                } else {
                    notices.add(route.provider() + " 호출 실패: " + ex.getMessage());
                }
                lastError = ex;
                continue;
            }

            JsonNode output = route.provider() == AiProviderType.OPENAI
                    ? result.output()
                    : JsonSchemaNormalizer.normalize(spec.request().schema(), result.output());
            BigDecimal cost = cost(route, result);
            List<String> schemaErrors = JsonSchemaValidator.validate(spec.request().schema(), output);
            if (!schemaErrors.isEmpty()) {
                log.warn("AI output failed schema validation type={} provider={} errors={}", spec.type(),
                        route.provider(), schemaErrors.subList(0, Math.min(5, schemaErrors.size())));
                usageLogService.recordFailure(spec.type(), inputHash, files.size(), route.provider(), result.model(),
                        spec.promptVersion(), "SCHEMA_VALIDATION", String.join("; ", schemaErrors),
                        System.currentTimeMillis() - started, result.attempts(), actorId, billingType(route));
                if (route.provider() != AiProviderType.MOCK) {
                    quotaService.recordAttempt(route.provider(), false, result.inputTokens(), result.outputTokens(),
                            cost);
                }
                if (reserved != null) {
                    quotaService.settleBudget(reserved, cost == null ? reserved : cost);
                }
                notices.add(route.provider() + " 결과가 형식 검증을 통과하지 못했습니다.");
                lastError = new AiClientException(AiClientException.Type.INVALID_RESPONSE,
                        "AI 결과가 형식 검증을 통과하지 못했습니다. 다시 시도해 주세요.");
                continue;
            }

            AiAnalysisHistory saved = usageLogService.recordSuccess(spec.type(), inputHash, files.size(),
                    route.provider(), result.model(), spec.promptVersion(), output.toString(), result.inputTokens(),
                    result.outputTokens(), System.currentTimeMillis() - started, result.attempts(), actorId, cost,
                    billingType(route));
            if (route.provider() != AiProviderType.MOCK) {
                quotaService.recordAttempt(route.provider(), true, result.inputTokens(), result.outputTokens(), cost);
            }
            if (reserved != null) {
                quotaService.settleBudget(reserved, cost == null ? reserved : cost);
            }
            return new Outcome(saved.getId(), output, route.provider(), result.model(), false, result.inputTokens(),
                    result.outputTokens(), cost, result.attempts(), billingType(route), List.copyOf(notices));
        }

        if (plan.paidApprovalAvailable() && !budgetBlocked) {
            throw unavailable(plan, notices, freeQuotaExhausted);
        }
        if (budgetBlocked) {
            throw new BusinessException(ErrorCode.AI_LIMIT_EXCEEDED, String.join(" ", notices)
                    + " 관리자 > AI 사용량 관리에서 예산을 확인해 주세요. 수동 입력은 계속 사용할 수 있습니다.");
        }
        if (freeQuotaExhausted) {
            throw new BusinessException(ErrorCode.AI_LIMIT_EXCEEDED,
                    "Gemini 무료 사용 한도를 초과했습니다. 유료 API(OpenAI)는 비활성화되어 있어 호출하지 않았습니다. "
                            + "관리자 > AI 사용량 관리에서 상태를 확인해 주세요. 수동 입력은 계속 사용할 수 있습니다.");
        }
        throw toBusiness(lastError);
    }

    Plan plan(AiSettingsService.Effective settings, boolean allowPaid) {
        List<Route> routes = new ArrayList<>();
        if (settings.mockEnabled()) {
            routes.add(new Route(AiProviderType.MOCK, "mock", false));
            return new Plan(routes, false, settings);
        }
        if (!settings.externalAllowed()) {
            return new Plan(routes, false, settings);
        }
        boolean openaiApproved = settings.paidFallbackEnabled() || allowPaid;
        Route gemini = settings.geminiUsable()
                ? new Route(AiProviderType.GEMINI, settings.geminiModel(), !settings.geminiFreeTier())
                : null;
        Route openai = settings.openaiUsable()
                ? new Route(AiProviderType.OPENAI, settings.openaiModel(), true)
                : null;
        if (gemini != null && gemini.paid() && !openaiApproved && settings.mode() != AiMode.GEMINI) {
            gemini = null;
        }
        boolean paidApprovalAvailable = false;
        if (settings.mode() == AiMode.OPENAI) {
            if (openai != null) {
                routes.add(openai);
            }
            if (gemini != null) {
                routes.add(gemini);
            }
        } else {
            if (gemini != null) {
                routes.add(gemini);
            }
            if (openai != null) {
                if (openaiApproved) {
                    routes.add(openai);
                } else {
                    paidApprovalAvailable = true;
                }
            }
        }
        return new Plan(routes, paidApprovalAvailable, settings);
    }

    private Optional<Outcome> findCached(Spec spec, AiSettingsService.Effective settings) {
        List<Route> candidates = new ArrayList<>();
        if (settings.mockEnabled()) {
            candidates.add(new Route(AiProviderType.MOCK, "mock", false));
        } else {
            candidates.add(new Route(AiProviderType.GEMINI, settings.geminiModel(), !settings.geminiFreeTier()));
            candidates.add(new Route(AiProviderType.OPENAI, settings.openaiModel(), true));
        }
        for (Route candidate : candidates) {
            var cached = usageLogService.findCached(spec.type(), inputHash(spec, candidate.provider(), candidate.model()));
            if (cached.isEmpty()) {
                continue;
            }
            AiAnalysisHistory history = cached.get();
            try {
                JsonNode output = objectMapper.readTree(history.getResultJson());
                quotaService.recordCacheHit(history.getProvider());
                return Optional.of(new Outcome(history.getId(), output, history.getProvider(), history.getModelName(),
                        true, history.getInputTokens(), history.getOutputTokens(), history.getEstimatedCostUsd(),
                        history.getAttemptCount(), history.getBillingType(),
                        List.of("이전 분석 결과를 재사용했습니다. (API 호출 없음)")));
            } catch (JsonProcessingException ex) {
                log.warn("Ignoring unreadable cached AI result id={}", history.getId());
            }
        }
        return Optional.empty();
    }

    private AiStructuredResult call(Route route, Spec spec) {
        return switch (route.provider()) {
            case MOCK -> new AiStructuredResult(spec.mockOutput().get(), "mock", null, null, 1);
            case GEMINI -> geminiClient.createStructured(spec.request());
            case OPENAI -> openAiClient.createStructured(spec.request());
            case LOCAL_OCR -> throw new AiClientException(AiClientException.Type.NOT_CONFIGURED,
                    "로컬 OCR은 이 경로로 호출할 수 없습니다.");
        };
    }

    /**
     * Cache key: SHA-256 of analysis type, prompt version, provider, model and the content hash (which is the
     * SHA-256 of the input files plus prompt inputs).
     */
    static String inputHash(Spec spec, AiProviderType provider, String model) {
        return UploadedFileInspector.sha256(
                spec.type() + "|" + spec.promptVersion() + "|" + provider + "|" + model + "|" + spec.contentHash());
    }

    private BigDecimal cost(Route route, AiStructuredResult result) {
        return switch (route.provider()) {
            case OPENAI -> AiUsageLogService.estimateCost(result.inputTokens(), result.outputTokens(),
                    properties.getInputPricePerMillion(), properties.getOutputPricePerMillion());
            case GEMINI -> route.paid()
                    ? AiUsageLogService.estimateCost(result.inputTokens(), result.outputTokens(),
                    costProperties.gemini().inputPricePerMillion(), costProperties.gemini().outputPricePerMillion())
                    : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
            default -> null;
        };
    }

    /** Worst-case cost of one call, reserved against the monthly budget before calling a paid API. */
    BigDecimal maxCost(Route route, AiStructuredRequest request) {
        long textChars = (request.instructions() == null ? 0 : request.instructions().length())
                + (request.userText() == null ? 0 : request.userText().length())
                + (request.schema() == null ? 0 : request.schema().toString().length());
        int inputTokens = (int) Math.min(Integer.MAX_VALUE, textChars / 2 + 200
                + (long) request.files().size() * TOKENS_PER_FILE_ESTIMATE);
        boolean gemini = route.provider() == AiProviderType.GEMINI;
        int outputTokens = gemini ? costProperties.gemini().maxOutputTokens() : properties.getMaxOutputTokens();
        BigDecimal inPrice = gemini ? costProperties.gemini().inputPricePerMillion() : properties.getInputPricePerMillion();
        BigDecimal outPrice = gemini ? costProperties.gemini().outputPricePerMillion() : properties.getOutputPricePerMillion();
        return AiUsageLogService.estimateCost(inputTokens, outputTokens, inPrice, outPrice);
    }

    private static String billingType(Route route) {
        return switch (route.provider()) {
            case MOCK -> "NONE";
            case LOCAL_OCR -> "LOCAL";
            default -> route.paid() ? "PAID" : "FREE";
        };
    }

    private BusinessException unavailable(Plan plan, List<String> notices, boolean freeQuotaExhausted) {
        AiSettingsService.Effective s = plan.settings();
        if (plan.paidApprovalAvailable()) {
            String reason = freeQuotaExhausted ? "무료 AI(Gemini) 사용 한도를 초과했습니다."
                    : s.geminiUsable() ? "무료 AI(Gemini) 분석에 실패했습니다."
                    : "무료 AI(Gemini)가 설정되어 있지 않습니다.";
            return new BusinessException(ErrorCode.AI_PAID_APPROVAL_REQUIRED,
                    reason + " 유료 API(OpenAI)로 분석하려면 승인해 주세요. 승인 시 비용이 발생할 수 있습니다. "
                            + "수동 입력은 계속 사용할 수 있습니다.");
        }
        if (!notices.isEmpty()) {
            return new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    String.join(" ", notices) + " 수동 입력은 계속 사용할 수 있습니다.");
        }
        String why = switch (s.mode()) {
            case MANUAL -> "AI가 수동 입력 모드로 설정되어 있습니다.";
            case LOCAL_ONLY -> "AI가 로컬 OCR 전용 모드로 설정되어 있어 외부 AI를 호출하지 않습니다.";
            default -> "사용 가능한 AI 제공업체가 없습니다. (Gemini 키 " + (s.geminiKeyConfigured() ? "설정됨" : "미설정")
                    + ", OpenAI " + (s.openaiUsable() ? "사용" : "비활성") + ")";
        };
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, why + " 수동 입력은 계속 사용할 수 있습니다.");
    }

    private static BusinessException toBusiness(AiClientException ex) {
        if (ex == null) {
            return new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "AI 분석에 실패했습니다. 수동 입력은 계속 사용할 수 있습니다.");
        }
        ErrorCode code = switch (ex.getType()) {
            case NOT_CONFIGURED -> ErrorCode.SERVICE_UNAVAILABLE;
            case RATE_LIMITED -> ErrorCode.TOO_MANY_REQUESTS;
            default -> ErrorCode.EXTERNAL_SERVICE_ERROR;
        };
        return new BusinessException(code, ex.getMessage());
    }
}
