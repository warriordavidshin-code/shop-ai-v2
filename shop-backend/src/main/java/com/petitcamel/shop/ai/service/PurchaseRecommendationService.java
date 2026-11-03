package com.petitcamel.shop.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.ai.client.AiStructuredRequest;
import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.domain.AiAnalysisType;
import com.petitcamel.shop.ai.domain.PurchaseRecommendation;
import com.petitcamel.shop.ai.repository.PurchaseRecommendationRepository;
import com.petitcamel.shop.ai.support.StrictSchema;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.common.storage.UploadedFileInspector;
import com.petitcamel.shop.inventory.dto.InventoryAnalyticsDtos;
import com.petitcamel.shop.inventory.service.InventoryAnalyticsService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 발주 추천. Quantities and priorities are computed by rules from sales speed, stock, inbound and lead time;
 * AI (when configured) only rewrites the explanation. Recommendations are stored for review and an admin
 * decision — nothing here creates a purchase order.
 */
@Service
public class PurchaseRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseRecommendationService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String PROMPT_VERSION = "purchase-reco-v1";

    public record RecommendationResponse(
            Long id,
            String type,
            Long productId,
            String productName,
            Long skuId,
            String optionName,
            int priority,
            Integer suggestedQuantity,
            JsonNode metrics,
            String reason,
            String provider,
            String status,
            Instant createdAt,
            Instant decidedAt
    ) {
    }

    public record GenerateResponse(String provider, boolean aiUsed, String summary, List<String> warnings,
                                   List<RecommendationResponse> recommendations) {
    }

    private final InventoryAnalyticsService inventoryAnalytics;
    private final AiAnalysisService aiAnalysisService;
    private final AiAnalysisProperties aiProperties;
    private final PurchaseRecommendationRepository repository;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PurchaseRecommendationService(InventoryAnalyticsService inventoryAnalytics,
                                         AiAnalysisService aiAnalysisService, AiAnalysisProperties aiProperties,
                                         PurchaseRecommendationRepository repository, AuditLogService auditLogService,
                                         EntityManager entityManager, ObjectMapper objectMapper, Clock clock) {
        this.inventoryAnalytics = inventoryAnalytics;
        this.aiAnalysisService = aiAnalysisService;
        this.aiProperties = aiProperties;
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public GenerateResponse generate(boolean useAi, Long actorId) {
        var lowStock = inventoryAnalytics.lowStock();
        int window = lowStock.windowDays();
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        Map<Long, Integer> previousSales = salesBetween(today.minusDays(2L * window - 1), today.minusDays(window - 1L));
        Map<Long, Object[]> productInfo = productInfo();
        Map<Long, Integer> stockouts = stockoutCounts(today.minusDays(89));

        List<ObjectNode> metricsList = new ArrayList<>();
        List<InventoryAnalyticsDtos.LowStockRow> rows = lowStock.rows().stream()
                .filter(r -> r.suggestedQuantity() > 0).limit(50).toList();
        for (InventoryAnalyticsDtos.LowStockRow row : rows) {
            ObjectNode m = objectMapper.createObjectNode();
            Object[] info = productInfo.get(row.skuId());
            int prev = previousSales.getOrDefault(row.skuId(), 0);
            m.put("sku_id", row.skuId());
            m.put("product_name", row.productName());
            m.put("option", row.optionName());
            m.put("available", row.availableQuantity());
            m.put("pending_inbound", row.pendingInbound());
            m.put("sales_recent", row.salesInWindow());
            m.put("sales_previous", prev);
            m.put("window_days", window);
            m.put("daily_sales", row.dailySales());
            if (row.coverDays() != null) {
                m.put("cover_days", row.coverDays());
            } else {
                m.putNull("cover_days");
            }
            m.put("lead_days", row.leadDays());
            m.put("safety_stock", row.safetyStock());
            m.put("suggested_quantity", row.suggestedQuantity());
            m.put("stockouts_90d", stockouts.getOrDefault(row.skuId(), 0));
            BigDecimal margin = info == null ? null : marginRate((BigDecimal) info[1], (BigDecimal) info[2]);
            if (margin != null) {
                m.put("margin_rate", margin);
            } else {
                m.putNull("margin_rate");
            }
            m.put("season", info == null || info[0] == null ? "" : (String) info[0]);
            m.put("supplier", row.supplierName() == null ? "" : row.supplierName());
            metricsList.add(m);
        }

        Map<Long, String> aiReasons = new HashMap<>();
        Map<Long, Integer> aiPriority = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        String summary = null;
        String provider = "RULE";
        Long analysisId = null;
        boolean aiUsed = false;
        if (useAi && !metricsList.isEmpty()) {
            if (!aiProperties.isAvailable()) {
                warnings.add("OpenAI API 키가 없어 규칙 기반 사유만 표시합니다.");
            } else {
                try {
                    var outcome = aiAnalysisService.run(spec(metricsList), actorId, false);
                    for (JsonNode node : outcome.output().path("recommendations")) {
                        long skuId = node.path("sku_id").asLong();
                        aiReasons.put(skuId, node.path("reason").asText());
                        aiPriority.put(skuId, node.path("priority").asInt());
                    }
                    summary = outcome.output().path("summary").asText(null);
                    provider = outcome.mock() ? "MOCK" : "OPENAI";
                    analysisId = outcome.analysisId();
                    aiUsed = true;
                    if (outcome.mock()) {
                        warnings.add("Mock 모드 결과입니다. 실제 AI 분석이 아닙니다.");
                    }
                } catch (BusinessException ex) {
                    log.warn("AI purchase recommendation failed, using rule reasons: {}", ex.getMessage());
                    warnings.add("AI 사유 생성에 실패해 규칙 기반 사유를 표시합니다: " + ex.getMessage());
                }
            }
        }

        List<RecommendationResponse> saved = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            InventoryAnalyticsDtos.LowStockRow row = rows.get(i);
            ObjectNode metrics = metricsList.get(i);
            String ruleReason = ruleReason(row, metrics);
            String aiReason = aiReasons.get(row.skuId());
            PurchaseRecommendation entity = new PurchaseRecommendation();
            entity.setRecommendationType("PURCHASE");
            entity.setProductId(row.productId());
            entity.setSkuId(row.skuId());
            entity.setPriority(aiPriority.getOrDefault(row.skuId(), rulePriority(row)));
            entity.setSuggestedQuantity(row.suggestedQuantity());
            entity.setMetricsJson(metrics.toString());
            String reason = aiReason == null || aiReason.isBlank() ? ruleReason
                    : aiReason + "\n[근거] " + ruleReason;
            entity.setReason(reason.length() > 2000 ? reason.substring(0, 2000) : reason);
            entity.setProvider(aiReason == null ? "RULE" : provider);
            entity.setAiAnalysisId(aiReason == null ? null : analysisId);
            entity.setCreatedBy(actorId);
            entity.setCreatedAt(clock.instant());
            repository.save(entity);
            saved.add(toResponse(entity, row.productName(), row.optionName()));
        }
        auditLogService.record(actorId, "PURCHASE_RECOMMENDATION_GENERATE", "AI_RECOMMENDATION", "PURCHASE",
                "count=" + saved.size() + ", provider=" + provider);
        if (saved.isEmpty()) {
            warnings.add("현재 기준으로 발주가 필요한 SKU가 없습니다.");
        }
        warnings.add("추천은 참고용이며 실제 발주는 관리자가 직접 진행해야 합니다.");
        return new GenerateResponse(provider, aiUsed, summary, warnings, saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecommendationResponse> list(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 30 : Math.min(size, 100));
        Page<PurchaseRecommendation> result = status == null || status.isBlank()
                ? repository.findByRecommendationTypeOrderByCreatedAtDescIdDesc("PURCHASE", pageable)
                : repository.findByRecommendationTypeAndStatusOrderByCreatedAtDescIdDesc("PURCHASE", status, pageable);
        Map<Long, Object[]> names = skuNames();
        return PageResponse.of(result.getContent().stream().map(r -> {
            Object[] n = r.getSkuId() == null ? null : names.get(r.getSkuId());
            return toResponse(r, n == null ? null : (String) n[0], n == null ? null : (String) n[1]);
        }).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public RecommendationResponse decide(Long id, String decision, Long actorId) {
        if (!"ACCEPTED".equals(decision) && !"DISMISSED".equals(decision)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "decision 은 ACCEPTED 또는 DISMISSED 여야 합니다.");
        }
        PurchaseRecommendation entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "추천 내역을 찾을 수 없습니다."));
        entity.setStatus(decision);
        entity.setDecidedBy(actorId);
        entity.setDecidedAt(clock.instant());
        repository.save(entity);
        auditLogService.record(actorId, "PURCHASE_RECOMMENDATION_" + decision, "AI_RECOMMENDATION",
                String.valueOf(id), "skuId=" + entity.getSkuId() + ", qty=" + entity.getSuggestedQuantity());
        Object[] n = entity.getSkuId() == null ? null : skuNames().get(entity.getSkuId());
        return toResponse(entity, n == null ? null : (String) n[0], n == null ? null : (String) n[1]);
    }

    private AiAnalysisService.Spec spec(List<ObjectNode> metricsList) {
        ArrayNode input = objectMapper.createArrayNode();
        metricsList.forEach(input::add);
        ObjectNode schema = StrictSchema.object()
                .prop("summary", StrictSchema.string("전체 발주 판단 요약 (한국어, 3문장 이내)"))
                .prop("recommendations", StrictSchema.array("SKU별 사유", StrictSchema.object()
                        .prop("sku_id", StrictSchema.integer("입력의 sku_id 그대로"))
                        .prop("priority", StrictSchema.integer("1(가장 시급)~5"))
                        .prop("reason", StrictSchema.string("수치 근거를 포함한 한국어 사유, 2문장 이내"))
                        .build()))
                .build();
        String instructions = """
                너는 여성의류 쇼핑몰의 재고 담당자를 돕는 분석가다. 입력된 수치만 근거로 발주 사유를 설명한다.
                - 입력에 없는 수치나 시장 정보를 만들지 않는다.
                - suggested_quantity 를 바꾸지 말고 왜 필요한지 설명한다.
                - 판매 증가(sales_recent vs sales_previous), 재고 소진일(cover_days), 리드타임, 품절 이력, 마진율, 시즌을 고려한다.
                - 결과는 참고용이며 실제 발주는 관리자가 결정한다.
                """;
        String userText = "다음 SKU 지표를 분석해 발주 사유를 작성하라:\n" + input;
        AiStructuredRequest request = new AiStructuredRequest("purchase_recommendation", schema, instructions,
                userText, List.of());
        return new AiAnalysisService.Spec(AiAnalysisType.PURCHASE_RECOMMENDATION, PROMPT_VERSION, request,
                UploadedFileInspector.sha256(input.toString()), () -> mockOutput(metricsList));
    }

    private JsonNode mockOutput(List<ObjectNode> metricsList) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("summary", "[MOCK] 실제 AI 분석이 아닌 테스트용 응답입니다. " + metricsList.size() + "개 SKU의 발주를 검토하세요.");
        ArrayNode recs = root.putArray("recommendations");
        for (ObjectNode m : metricsList) {
            ObjectNode r = recs.addObject();
            r.put("sku_id", m.path("sku_id").asLong());
            r.put("priority", 3);
            r.put("reason", "[MOCK] 최근 판매 " + m.path("sales_recent").asInt() + "개, 가용재고 "
                    + m.path("available").asInt() + "개 기준 테스트 사유입니다.");
        }
        return root;
    }

    private static String ruleReason(InventoryAnalyticsDtos.LowStockRow row, ObjectNode metrics) {
        StringBuilder sb = new StringBuilder(String.join(" / ", row.reasons()));
        int recent = metrics.path("sales_recent").asInt();
        int previous = metrics.path("sales_previous").asInt();
        if (recent > previous && previous > 0) {
            sb.append(" / 직전 ").append(row.windowDays()).append("일 대비 판매 ")
                    .append(Math.round((recent - previous) * 100.0 / previous)).append("% 증가");
        }
        int stockouts = metrics.path("stockouts_90d").asInt();
        if (stockouts > 0) {
            sb.append(" / 최근 90일 품절 ").append(stockouts).append("회");
        }
        sb.append(" → 권장 ").append(row.suggestedQuantity()).append("개");
        return sb.toString();
    }

    private static int rulePriority(InventoryAnalyticsDtos.LowStockRow row) {
        if (row.availableQuantity() <= 0) {
            return 1;
        }
        if (row.coverDays() != null && row.coverDays().compareTo(BigDecimal.valueOf(row.leadDays())) <= 0) {
            return 2;
        }
        return 3;
    }

    private static BigDecimal marginRate(BigDecimal salePrice, BigDecimal cost) {
        if (salePrice == null || cost == null || salePrice.signum() == 0) {
            return null;
        }
        return salePrice.subtract(cost).divide(salePrice, 4, RoundingMode.HALF_UP);
    }

    private Map<Long, Integer> salesBetween(LocalDate from, LocalDate toExclusive) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT oi.sku_id, COALESCE(SUM(oi.quantity), 0)
                FROM order_item oi
                JOIN (SELECT order_id, MIN(approved_at) AS approved_at FROM payment
                      WHERE approved_at IS NOT NULL GROUP BY order_id) pay ON pay.order_id = oi.order_id
                WHERE oi.status = 'ORDERED' AND pay.approved_at >= :start AND pay.approved_at < :end
                GROUP BY oi.sku_id
                """)
                .setParameter("start", Timestamp.from(from.atStartOfDay(ZONE).toInstant()))
                .setParameter("end", Timestamp.from(toExclusive.atStartOfDay(ZONE).toInstant()))
                .getResultList();
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] r : rows) {
            result.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
        }
        return result;
    }

    private Map<Long, Integer> stockoutCounts(LocalDate from) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT sku_id, COUNT(*) FROM inventory_movement
                WHERE stock_after = 0 AND stock_before > 0 AND created_at >= :start
                GROUP BY sku_id
                """).setParameter("start", Timestamp.from(from.atStartOfDay(ZONE).toInstant())).getResultList();
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] r : rows) {
            result.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
        }
        return result;
    }

    /** sku → [season, sale price incl. option price, unit cost]. */
    private Map<Long, Object[]> productInfo() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT s.sku_id, p.season, p.sale_price + COALESCE(s.additional_price, 0),
                       COALESCE(sc.avg_unit_cost, p.purchase_price)
                FROM product_sku s JOIN product p ON p.product_id = s.product_id
                LEFT JOIN sku_cost sc ON sc.sku_id = s.sku_id
                """).getResultList();
        Map<Long, Object[]> result = new HashMap<>();
        for (Object[] r : rows) {
            result.put(((Number) r[0]).longValue(), new Object[]{r[1], dec(r[2]), dec(r[3])});
        }
        return result;
    }

    private Map<Long, Object[]> skuNames() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT s.sku_id, p.product_name, s.color || '/' || s.size
                FROM product_sku s JOIN product p ON p.product_id = s.product_id
                """).getResultList();
        Map<Long, Object[]> result = new HashMap<>();
        for (Object[] r : rows) {
            result.put(((Number) r[0]).longValue(), new Object[]{r[1], r[2]});
        }
        return result;
    }

    private RecommendationResponse toResponse(PurchaseRecommendation r, String productName, String optionName) {
        JsonNode metrics;
        try {
            metrics = objectMapper.readTree(r.getMetricsJson());
        } catch (Exception ex) {
            metrics = objectMapper.createObjectNode();
        }
        return new RecommendationResponse(r.getId(), r.getRecommendationType(), r.getProductId(), productName,
                r.getSkuId(), optionName, r.getPriority(), r.getSuggestedQuantity(), metrics, r.getReason(),
                r.getProvider(), r.getStatus(), r.getCreatedAt(), r.getDecidedAt());
    }

    private static BigDecimal dec(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }
}
