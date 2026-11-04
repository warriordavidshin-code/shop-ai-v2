package com.petitcamel.shop.ai.service;

import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.config.AiCostProperties;
import com.petitcamel.shop.ai.domain.AiMode;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Effective AI routing settings: AI_* environment values are the defaults and an admin may override them
 * ({@code ai_settings}, NULL = use the environment). API keys are environment-only; only "configured or not"
 * is ever exposed.
 */
@Service
public class AiSettingsService {

    private static final BigDecimal MAX_BUDGET = new BigDecimal("10000");

    private final AiCostProperties cost;
    private final AiAnalysisProperties analysis;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiSettingsService(AiCostProperties cost, AiAnalysisProperties analysis, JdbcTemplate jdbc, Clock clock) {
        this.cost = cost;
        this.analysis = analysis;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Effective(
            AiMode mode,
            boolean localOcrEnabled,
            boolean geminiEnabled,
            boolean openaiEnabled,
            boolean paidFallbackEnabled,
            boolean cacheEnabled,
            int dailyRequestLimit,
            BigDecimal monthlyBudgetUsd,
            boolean geminiKeyConfigured,
            boolean openaiKeyConfigured,
            boolean geminiFreeTier,
            boolean mockEnabled,
            String geminiModel,
            String openaiModel
    ) {
        public boolean geminiUsable() {
            return geminiEnabled && geminiKeyConfigured;
        }

        public boolean openaiUsable() {
            return openaiEnabled && openaiKeyConfigured;
        }

        public boolean externalAllowed() {
            return mode != AiMode.MANUAL && mode != AiMode.LOCAL_ONLY;
        }

        public boolean localOcrAllowed() {
            return localOcrEnabled && (mode == AiMode.FREE_FIRST || mode == AiMode.LOCAL_ONLY);
        }
    }

    /** Raw override row; null fields fall back to the environment. */
    public record Overrides(
            String mode,
            Boolean localOcrEnabled,
            Boolean geminiEnabled,
            Boolean openaiEnabled,
            Boolean paidFallbackEnabled,
            Boolean cacheEnabled,
            Integer dailyRequestLimit,
            BigDecimal monthlyBudgetUsd
    ) {
    }

    @Transactional(readOnly = true)
    public Effective effective() {
        Overrides o = overrides();
        return new Effective(
                AiMode.parse(o.mode(), defaultMode()),
                pick(o.localOcrEnabled(), cost.localOcrEnabled()),
                pick(o.geminiEnabled(), cost.gemini().enabled()),
                pick(o.openaiEnabled(), cost.openaiEnabled()),
                pick(o.paidFallbackEnabled(), cost.paidFallbackEnabled()),
                pick(o.cacheEnabled(), cost.cacheEnabled()),
                o.dailyRequestLimit() != null ? o.dailyRequestLimit() : Math.max(0, cost.dailyRequestLimit()),
                o.monthlyBudgetUsd() != null ? o.monthlyBudgetUsd() : nonNegative(cost.monthlyBudgetUsd()),
                cost.gemini().hasApiKey(),
                analysis.hasApiKey(),
                cost.gemini().freeTier(),
                analysis.isMockEnabled(),
                cost.gemini().model(),
                analysis.effectiveModel());
    }

    @Transactional(readOnly = true)
    public Overrides overrides() {
        List<Overrides> rows = jdbc.query("""
                        select ai_mode, local_ocr_enabled, gemini_enabled, openai_enabled, paid_fallback_enabled,
                               cache_enabled, daily_request_limit, monthly_budget_usd
                        from ai_settings where settings_id = 1""",
                (rs, i) -> new Overrides(
                        rs.getString(1),
                        (Boolean) rs.getObject(2),
                        (Boolean) rs.getObject(3),
                        (Boolean) rs.getObject(4),
                        (Boolean) rs.getObject(5),
                        (Boolean) rs.getObject(6),
                        (Integer) rs.getObject(7),
                        rs.getBigDecimal(8)));
        return rows.isEmpty() ? new Overrides(null, null, null, null, null, null, null, null) : rows.get(0);
    }

    /** Environment defaults, for showing "기본값" next to each override in the admin screen. */
    public Map<String, Object> environmentDefaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", defaultMode().name());
        map.put("localOcrEnabled", cost.localOcrEnabled());
        map.put("geminiEnabled", cost.gemini().enabled());
        map.put("openaiEnabled", cost.openaiEnabled());
        map.put("paidFallbackEnabled", cost.paidFallbackEnabled());
        map.put("cacheEnabled", cost.cacheEnabled());
        map.put("dailyRequestLimit", Math.max(0, cost.dailyRequestLimit()));
        map.put("monthlyBudgetUsd", nonNegative(cost.monthlyBudgetUsd()));
        return map;
    }

    @Transactional
    public void update(Overrides request, Long actorId) {
        if (request.mode() != null && AiMode.parse(request.mode(), null) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "알 수 없는 AI 모드입니다.");
        }
        if (request.dailyRequestLimit() != null
                && (request.dailyRequestLimit() < 0 || request.dailyRequestLimit() > 100_000)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "일일 요청 한도는 0~100000 사이여야 합니다.");
        }
        if (request.monthlyBudgetUsd() != null && (request.monthlyBudgetUsd().signum() < 0
                || request.monthlyBudgetUsd().compareTo(MAX_BUDGET) > 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "월 예산은 0~10000 USD 사이여야 합니다.");
        }
        jdbc.update("""
                        update ai_settings set ai_mode = ?, local_ocr_enabled = ?, gemini_enabled = ?,
                               openai_enabled = ?, paid_fallback_enabled = ?, cache_enabled = ?,
                               daily_request_limit = ?, monthly_budget_usd = ?, updated_by = ?, updated_at = ?,
                               version = version + 1
                        where settings_id = 1""",
                request.mode() == null ? null : AiMode.parse(request.mode(), null).name(),
                request.localOcrEnabled(), request.geminiEnabled(), request.openaiEnabled(),
                request.paidFallbackEnabled(), request.cacheEnabled(), request.dailyRequestLimit(),
                request.monthlyBudgetUsd(), actorId, Timestamp.from(clock.instant()));
    }

    private AiMode defaultMode() {
        return AiMode.parse(cost.mode(), AiMode.FREE_FIRST);
    }

    private static boolean pick(Boolean override, boolean fallback) {
        return override != null ? override : fallback;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
