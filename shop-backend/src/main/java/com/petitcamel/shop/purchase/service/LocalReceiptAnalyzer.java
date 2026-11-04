package com.petitcamel.shop.purchase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.ai.client.AiClientException;
import com.petitcamel.shop.ai.client.AiInputFile;
import com.petitcamel.shop.ai.client.LocalOcrClient;
import com.petitcamel.shop.ai.config.AiCostProperties;
import com.petitcamel.shop.ai.domain.AiAnalysisHistory;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import com.petitcamel.shop.ai.domain.AiProviderType;
import com.petitcamel.shop.ai.service.AiAnalysisService;
import com.petitcamel.shop.ai.service.AiQuotaService;
import com.petitcamel.shop.ai.service.AiUsageLogService;
import com.petitcamel.shop.ai.support.JsonSchemaValidator;
import com.petitcamel.shop.common.storage.UploadedFileInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Step 1 of receipt analysis: PaddleOCR on this server + {@link ReceiptTextParser} rules. Free, no external
 * API. Results are cached like AI results (file SHA-256 + rule version). Returns empty when the OCR service
 * is unreachable so the caller can fall back to Gemini or manual input.
 */
@Service
public class LocalReceiptAnalyzer {

    static final String RULE_VERSION = "receipt-ocr-v1";
    static final String MODEL = "paddleocr+rules";
    private static final Logger log = LoggerFactory.getLogger(LocalReceiptAnalyzer.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public record Result(AiAnalysisService.Outcome outcome, boolean uncertain, List<String> reasons, String ocrText) {
    }

    private final LocalOcrClient ocrClient;
    private final AiCostProperties costProperties;
    private final AiUsageLogService usageLogService;
    private final AiQuotaService quotaService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalReceiptAnalyzer(LocalOcrClient ocrClient, AiCostProperties costProperties,
                                AiUsageLogService usageLogService, AiQuotaService quotaService,
                                ObjectMapper objectMapper, Clock clock) {
        this.ocrClient = ocrClient;
        this.costProperties = costProperties;
        this.usageLogService = usageLogService;
        this.quotaService = quotaService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Optional<Result> analyze(List<AiInputFile> files, String contentHash, boolean useCache, Long actorId) {
        String inputHash = UploadedFileInspector.sha256(
                AiAnalysisType.RECEIPT + "|" + RULE_VERSION + "|" + AiProviderType.LOCAL_OCR + "|" + MODEL + "|" + contentHash);
        if (useCache) {
            Optional<AiAnalysisHistory> cached = usageLogService.findCached(AiAnalysisType.RECEIPT, inputHash);
            if (cached.isPresent()) {
                try {
                    JsonNode stored = objectMapper.readTree(cached.get().getResultJson());
                    quotaService.recordCacheHit(AiProviderType.LOCAL_OCR);
                    return Optional.of(toResult(cached.get().getId(), stored, true));
                } catch (JsonProcessingException ex) {
                    log.warn("Ignoring unreadable cached OCR result id={}", cached.get().getId());
                }
            }
        }

        long started = System.currentTimeMillis();
        List<LocalOcrClient.OcrResult> ocr = new ArrayList<>();
        try {
            for (AiInputFile file : files) {
                ocr.add(ocrClient.recognize(file));
            }
        } catch (AiClientException ex) {
            log.warn("Local OCR unavailable type={} message={}", ex.getType(), ex.getMessage());
            usageLogService.recordFailure(AiAnalysisType.RECEIPT, inputHash, files.size(), AiProviderType.LOCAL_OCR,
                    MODEL, RULE_VERSION, ex.getType().name(), ex.getMessage(), System.currentTimeMillis() - started,
                    1, actorId, "LOCAL");
            quotaService.recordAttempt(AiProviderType.LOCAL_OCR, false, null, null, null);
            return Optional.empty();
        }

        ReceiptTextParser parser = new ReceiptTextParser(objectMapper, costProperties.ocr().minConfidence(),
                LocalDate.now(clock.withZone(ZONE)));
        ReceiptTextParser.Parsed parsed = parser.parse(ocr);
        List<String> schemaErrors = JsonSchemaValidator.validate(AiReceiptService.schema(), parsed.output());
        if (!schemaErrors.isEmpty()) {
            log.error("Receipt rule parser produced invalid output errors={}",
                    schemaErrors.subList(0, Math.min(5, schemaErrors.size())));
            quotaService.recordAttempt(AiProviderType.LOCAL_OCR, false, null, null, null);
            return Optional.empty();
        }
        ObjectNode stored = objectMapper.createObjectNode();
        stored.set("output", parsed.output());
        stored.put("uncertain", parsed.uncertain());
        stored.set("reasons", objectMapper.valueToTree(parsed.reasons()));
        stored.put("ocr_text", parsed.ocrText());
        AiAnalysisHistory saved = usageLogService.recordSuccess(AiAnalysisType.RECEIPT, inputHash, files.size(),
                AiProviderType.LOCAL_OCR, MODEL, RULE_VERSION, stored.toString(), null, null,
                System.currentTimeMillis() - started, 1, actorId, BigDecimal.ZERO, "LOCAL");
        quotaService.recordAttempt(AiProviderType.LOCAL_OCR, true, null, null, BigDecimal.ZERO);
        return Optional.of(toResult(saved.getId(), stored, false));
    }

    private Result toResult(Long analysisId, JsonNode stored, boolean cached) {
        List<String> reasons = new ArrayList<>();
        stored.path("reasons").forEach(r -> reasons.add(r.asText()));
        List<String> notices = new ArrayList<>();
        notices.add(cached ? "이전 로컬 OCR 결과를 재사용했습니다. (비용 없음)"
                : "로컬 OCR(PaddleOCR)과 규칙으로 분석했습니다. (외부 AI 호출 없음, 비용 없음)");
        AiAnalysisService.Outcome outcome = new AiAnalysisService.Outcome(analysisId, stored.path("output"),
                AiProviderType.LOCAL_OCR, MODEL, cached, null, null, BigDecimal.ZERO, 1, "LOCAL", notices);
        return new Result(outcome, stored.path("uncertain").asBoolean(), reasons, stored.path("ocr_text").asText(""));
    }
}
