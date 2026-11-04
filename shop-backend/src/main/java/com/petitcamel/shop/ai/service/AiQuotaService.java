package com.petitcamel.shop.ai.service;

import com.petitcamel.shop.ai.domain.AiProviderType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily request limit, monthly paid budget and per-provider usage counters ({@code ai_usage_daily},
 * {@code ai_budget_month}). Every check is a single conditional UPDATE committed in its own transaction, so
 * concurrent requests can never push the counters past the limit and the counts survive a failing caller.
 */
@Service
public class AiQuotaService {

    public static final String EXTERNAL = "EXTERNAL";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AiQuotaService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public String currentMonth() {
        return YearMonth.now(clock.withZone(ZONE)).toString();
    }

    /** Takes one slot of today's external-call limit; false when the limit is reached. */
    public boolean tryAcquireDaily(int limit) {
        if (limit <= 0) {
            return false;
        }
        return Boolean.TRUE.equals(tx.execute(status -> {
            ensureRow(EXTERNAL);
            return jdbc.update("""
                    update ai_usage_daily set request_count = request_count + 1
                    where usage_date = ? and provider = ? and request_count < ?""",
                    Date.valueOf(today()), EXTERNAL, limit) == 1;
        }));
    }

    /** Gives back a slot taken by {@link #tryAcquireDaily} when no call was made after all. */
    public void releaseDaily() {
        tx.executeWithoutResult(status -> jdbc.update("""
                update ai_usage_daily set request_count = request_count - 1
                where usage_date = ? and provider = ? and request_count > 0""",
                Date.valueOf(today()), EXTERNAL));
    }

    public int dailyUsed() {
        List<Integer> rows = jdbc.queryForList(
                "select request_count from ai_usage_daily where usage_date = ? and provider = ?",
                Integer.class, Date.valueOf(today()), EXTERNAL);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    /** Reserves the worst-case cost of a paid call; false when it would exceed the monthly budget. */
    public boolean tryReserveBudget(BigDecimal amount, BigDecimal budget) {
        String month = currentMonth();
        return Boolean.TRUE.equals(tx.execute(status -> {
            jdbc.update("insert into ai_budget_month (budget_month) values (?) on conflict do nothing", month);
            int updated = jdbc.update("""
                    update ai_budget_month set reserved_usd = reserved_usd + ?, updated_at = ?
                    where budget_month = ? and spent_usd + reserved_usd + ? <= ?""",
                    amount, now(), month, amount, budget);
            if (updated == 0) {
                jdbc.update("update ai_budget_month set blocked_count = blocked_count + 1, updated_at = ? "
                        + "where budget_month = ?", now(), month);
            }
            return updated == 1;
        }));
    }

    /** Replaces a reservation with the actual cost (0 when the call failed before being billed). */
    public void settleBudget(BigDecimal reserved, BigDecimal actual) {
        String month = currentMonth();
        BigDecimal spent = actual == null ? BigDecimal.ZERO : actual;
        tx.executeWithoutResult(status -> jdbc.update("""
                update ai_budget_month set reserved_usd = greatest(reserved_usd - ?, 0),
                       spent_usd = spent_usd + ?, updated_at = ?
                where budget_month = ?""", reserved, spent, now(), month));
    }

    public void recordAttempt(AiProviderType provider, boolean success, Integer inputTokens, Integer outputTokens,
                              BigDecimal estimatedCost) {
        tx.executeWithoutResult(status -> {
            ensureRow(provider.name());
            jdbc.update("""
                    update ai_usage_daily set request_count = request_count + 1,
                           success_count = success_count + ?, failure_count = failure_count + ?,
                           input_tokens = input_tokens + ?, output_tokens = output_tokens + ?,
                           estimated_cost_usd = estimated_cost_usd + ?
                    where usage_date = ? and provider = ?""",
                    success ? 1 : 0, success ? 0 : 1,
                    inputTokens == null ? 0 : inputTokens, outputTokens == null ? 0 : outputTokens,
                    estimatedCost == null ? BigDecimal.ZERO : estimatedCost,
                    Date.valueOf(today()), provider.name());
        });
    }

    public void recordCacheHit(AiProviderType provider) {
        increment(provider.name(), "cache_hits");
    }

    public void recordBlocked(AiProviderType provider) {
        increment(provider.name(), "blocked_count");
    }

    public void markQuotaExhausted(AiProviderType provider) {
        tx.executeWithoutResult(status -> {
            ensureRow(provider.name());
            jdbc.update("update ai_usage_daily set quota_exhausted_at = coalesce(quota_exhausted_at, ?) "
                    + "where usage_date = ? and provider = ?", now(), Date.valueOf(today()), provider.name());
        });
    }

    public boolean isQuotaExhaustedToday(AiProviderType provider) {
        Integer count = jdbc.queryForObject("""
                select count(*) from ai_usage_daily
                where usage_date = ? and provider = ? and quota_exhausted_at is not null""",
                Integer.class, Date.valueOf(today()), provider.name());
        return count != null && count > 0;
    }

    private void increment(String provider, String column) {
        tx.executeWithoutResult(status -> {
            ensureRow(provider);
            jdbc.update("update ai_usage_daily set " + column + " = " + column + " + 1 "
                    + "where usage_date = ? and provider = ?", Date.valueOf(today()), provider);
        });
    }

    private void ensureRow(String provider) {
        jdbc.update("insert into ai_usage_daily (usage_date, provider) values (?, ?) on conflict do nothing",
                Date.valueOf(today()), provider);
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }
}
