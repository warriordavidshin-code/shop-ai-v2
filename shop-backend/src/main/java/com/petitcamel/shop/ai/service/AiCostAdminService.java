package com.petitcamel.shop.ai.service;

import com.petitcamel.shop.ai.client.LocalOcrClient;
import com.petitcamel.shop.ai.domain.AiMode;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Data for the "AI 사용량 관리" and "AI 환경설정" admin screens. Estimates and actual invoices are kept apart. */
@Service
public class AiCostAdminService {

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-\\d{2}$");

    private final JdbcTemplate jdbc;
    private final AiSettingsService settingsService;
    private final AiQuotaService quotaService;
    private final LocalOcrClient ocrClient;
    private final Clock clock;

    public AiCostAdminService(JdbcTemplate jdbc, AiSettingsService settingsService, AiQuotaService quotaService,
                              LocalOcrClient ocrClient, Clock clock) {
        this.jdbc = jdbc;
        this.settingsService = settingsService;
        this.quotaService = quotaService;
        this.ocrClient = ocrClient;
        this.clock = clock;
    }

    public record SettingsView(AiSettingsService.Effective effective, AiSettingsService.Overrides overrides,
                               Map<String, Object> environmentDefaults, boolean localOcrHealthy,
                               List<String> modes) {
    }

    public record ProviderUsage(String provider, String model, boolean enabled, boolean keyConfigured,
                                String billing, long todayCalls, long todaySuccess, long todayFailures,
                                long todayCacheHits, long todayBlocked, long todayInputTokens, long todayOutputTokens,
                                BigDecimal todayEstimatedCostUsd, long monthCalls, long monthCacheHits,
                                long monthInputTokens, long monthOutputTokens, BigDecimal monthEstimatedCostUsd,
                                BigDecimal totalEstimatedCostUsd, boolean quotaExhaustedToday) {
    }

    public record Budget(String month, BigDecimal budgetUsd, BigDecimal spentUsd, BigDecimal reservedUsd,
                         BigDecimal remainingUsd, long blockedCount, boolean exceeded) {
    }

    public record BillingRecord(Long id, String provider, String billingMonth, BigDecimal amountUsd, String memo,
                                Instant recordedAt) {
    }

    public record CostUsage(LocalDate today, String month, AiMode mode, boolean paidApiEnabled,
                            boolean paidFallbackEnabled, boolean cacheEnabled, int dailyRequestLimit,
                            int dailyRequestsUsed, boolean dailyLimitReached, Budget budget,
                            List<ProviderUsage> providers, BigDecimal monthEstimatedCostUsd,
                            BigDecimal monthActualBilledUsd, List<BillingRecord> billingRecords,
                            List<String> alerts, String note) {
    }

    public record BillingRequest(String provider, String billingMonth, BigDecimal amountUsd, String memo) {
    }

    public SettingsView settings() {
        return new SettingsView(settingsService.effective(), settingsService.overrides(),
                settingsService.environmentDefaults(), ocrClient.isHealthy(),
                List.of("FREE_FIRST", "GEMINI", "OPENAI", "LOCAL_ONLY", "MANUAL"));
    }

    @Transactional(readOnly = true)
    public CostUsage usage() {
        AiSettingsService.Effective s = settingsService.effective();
        LocalDate today = quotaService.today();
        String month = quotaService.currentMonth();
        LocalDate monthStart = YearMonth.parse(month).atDay(1);

        Map<String, long[]> todayCounts = new HashMap<>();
        Map<String, BigDecimal> todayCost = new HashMap<>();
        Map<String, Boolean> exhausted = new HashMap<>();
        jdbc.query("""
                select provider, request_count, success_count, failure_count, cache_hits, blocked_count,
                       input_tokens, output_tokens, estimated_cost_usd, quota_exhausted_at
                from ai_usage_daily where usage_date = ?""", rs -> {
            String p = rs.getString(1);
            todayCounts.put(p, new long[]{rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6),
                    rs.getLong(7), rs.getLong(8)});
            todayCost.put(p, rs.getBigDecimal(9));
            exhausted.put(p, rs.getTimestamp(10) != null);
        }, Date.valueOf(today));

        Map<String, long[]> monthCounts = new HashMap<>();
        Map<String, BigDecimal> monthCost = new HashMap<>();
        jdbc.query("""
                select provider, sum(request_count), sum(cache_hits), sum(input_tokens), sum(output_tokens),
                       sum(estimated_cost_usd)
                from ai_usage_daily where usage_date >= ? group by provider""", rs -> {
            monthCounts.put(rs.getString(1), new long[]{rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)});
            monthCost.put(rs.getString(1), rs.getBigDecimal(6));
        }, Date.valueOf(monthStart));

        Map<String, BigDecimal> totalCost = new HashMap<>();
        jdbc.query("select provider, coalesce(sum(estimated_cost_usd), 0) from ai_analysis_history "
                + "where analysis_status = 'SUCCEEDED' group by provider", rs -> {
            totalCost.put(rs.getString(1), rs.getBigDecimal(2));
        });

        List<ProviderUsage> providers = new ArrayList<>();
        providers.add(provider("LOCAL_OCR", "paddleocr+rules", s.localOcrEnabled(), true, "LOCAL",
                todayCounts, todayCost, monthCounts, monthCost, totalCost, exhausted));
        providers.add(provider("GEMINI", s.geminiModel(), s.geminiEnabled(), s.geminiKeyConfigured(),
                s.geminiFreeTier() ? "FREE" : "PAID", todayCounts, todayCost, monthCounts, monthCost, totalCost,
                exhausted));
        providers.add(provider("OPENAI", s.openaiModel(), s.openaiEnabled(), s.openaiKeyConfigured(), "PAID",
                todayCounts, todayCost, monthCounts, monthCost, totalCost, exhausted));
        if (s.mockEnabled() || todayCounts.containsKey("MOCK")) {
            providers.add(provider("MOCK", "mock", s.mockEnabled(), true, "NONE",
                    todayCounts, todayCost, monthCounts, monthCost, totalCost, exhausted));
        }

        Budget budget = budget(month, s.monthlyBudgetUsd());
        BigDecimal monthEstimated = providers.stream().map(ProviderUsage::monthEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BillingRecord> records = billingRecords(12);
        BigDecimal monthActual = records.stream().filter(r -> month.equals(r.billingMonth()))
                .map(BillingRecord::amountUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        int dailyUsed = quotaService.dailyUsed();
        boolean dailyReached = dailyUsed >= s.dailyRequestLimit();

        List<String> alerts = new ArrayList<>();
        if (Boolean.TRUE.equals(exhausted.get("GEMINI"))) {
            alerts.add("오늘 Gemini 무료 사용 한도를 초과했습니다. 내일 다시 사용 가능하며, 유료 API는 승인 없이 호출하지 않습니다.");
        }
        if (dailyReached) {
            alerts.add("오늘 외부 AI 요청 한도(" + s.dailyRequestLimit() + "회)에 도달했습니다.");
        }
        if (budget.exceeded()) {
            alerts.add("이번 달 AI 예산을 모두 사용하여 새 유료 호출이 차단됩니다.");
        }
        if (!s.geminiKeyConfigured()) {
            alerts.add("GEMINI_API_KEY가 설정되지 않아 무료 AI 분석을 사용할 수 없습니다. 영수증은 로컬 OCR로 처리됩니다.");
        }
        if (s.paidFallbackEnabled() && s.openaiUsable()) {
            alerts.add("유료 API 자동 전환(AI_PAID_FALLBACK_ENABLED)이 켜져 있어 Gemini 실패 시 OpenAI가 자동 호출될 수 있습니다.");
        }
        return new CostUsage(today, month, s.mode(), s.openaiUsable(), s.paidFallbackEnabled(), s.cacheEnabled(),
                s.dailyRequestLimit(), dailyUsed, dailyReached, budget, providers, monthEstimated, monthActual,
                records, alerts,
                "추정 비용은 토큰 수 × 설정 단가로 계산한 값입니다. 실제 청구액은 각 제공업체 결제 화면의 금액을 "
                        + "[실제 청구 금액 입력]으로 별도 기록해 주세요. Gemini 무료 등급과 로컬 OCR은 비용 0으로 집계됩니다.");
    }

    @Transactional
    public BillingRecord addBilling(BillingRequest request, Long actorId) {
        if (request == null || request.provider() == null
                || !List.of("OPENAI", "GEMINI").contains(request.provider().trim().toUpperCase())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "제공업체는 OPENAI 또는 GEMINI만 입력할 수 있습니다.");
        }
        if (request.billingMonth() == null || !MONTH.matcher(request.billingMonth().trim()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "청구 월은 YYYY-MM 형식으로 입력해 주세요.");
        }
        if (request.amountUsd() == null || request.amountUsd().signum() < 0
                || request.amountUsd().compareTo(new BigDecimal("100000")) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "청구 금액(USD)을 확인해 주세요.");
        }
        String memo = request.memo() == null ? null
                : request.memo().length() > 300 ? request.memo().substring(0, 300) : request.memo();
        Instant now = clock.instant();
        Long id = jdbc.queryForObject("""
                insert into ai_billing_record (provider, billing_month, amount_usd, memo, recorded_by, recorded_at)
                values (?, ?, ?, ?, ?, ?) returning billing_id""", Long.class,
                request.provider().trim().toUpperCase(), request.billingMonth().trim(),
                request.amountUsd().setScale(4, RoundingMode.HALF_UP), memo, actorId, Timestamp.from(now));
        return new BillingRecord(id, request.provider().trim().toUpperCase(), request.billingMonth().trim(),
                request.amountUsd().setScale(4, RoundingMode.HALF_UP), memo, now);
    }

    @Transactional(readOnly = true)
    public List<BillingRecord> billingRecords(int months) {
        String from = YearMonth.now(clock.withZone(java.time.ZoneId.of("Asia/Seoul")))
                .minusMonths(Math.max(0, months - 1)).toString();
        return jdbc.query("""
                select billing_id, provider, billing_month, amount_usd, memo, recorded_at
                from ai_billing_record where billing_month >= ? order by billing_month desc, billing_id desc""",
                (rs, i) -> new BillingRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getString(5), rs.getTimestamp(6).toInstant()), from);
    }

    private Budget budget(String month, BigDecimal budgetUsd) {
        List<Budget> rows = jdbc.query("select spent_usd, reserved_usd, blocked_count from ai_budget_month "
                + "where budget_month = ?", (rs, i) -> {
            BigDecimal spent = rs.getBigDecimal(1);
            BigDecimal reserved = rs.getBigDecimal(2);
            BigDecimal remaining = budgetUsd.subtract(spent).subtract(reserved).max(BigDecimal.ZERO);
            return new Budget(month, budgetUsd, spent, reserved, remaining, rs.getLong(3),
                    spent.add(reserved).compareTo(budgetUsd) >= 0);
        }, month);
        if (rows.isEmpty()) {
            return new Budget(month, budgetUsd, BigDecimal.ZERO, BigDecimal.ZERO, budgetUsd, 0,
                    budgetUsd.signum() <= 0);
        }
        return rows.get(0);
    }

    private static ProviderUsage provider(String name, String model, boolean enabled, boolean keyConfigured,
                                          String billing, Map<String, long[]> today, Map<String, BigDecimal> todayCost,
                                          Map<String, long[]> month, Map<String, BigDecimal> monthCost,
                                          Map<String, BigDecimal> totalCost, Map<String, Boolean> exhausted) {
        long[] t = today.getOrDefault(name, new long[7]);
        long[] m = month.getOrDefault(name, new long[4]);
        return new ProviderUsage(name, model, enabled, keyConfigured, billing, t[0], t[1], t[2], t[3], t[4], t[5],
                t[6], zero(todayCost.get(name)), m[0], m[1], m[2], m[3], zero(monthCost.get(name)),
                zero(totalCost.get(name)), Boolean.TRUE.equals(exhausted.get(name)));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
