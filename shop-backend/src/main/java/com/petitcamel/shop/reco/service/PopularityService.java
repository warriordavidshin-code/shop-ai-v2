package com.petitcamel.shop.reco.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.reco.support.ProductProfile;
import com.petitcamel.shop.reco.support.ProductTraits;
import com.petitcamel.shop.reco.support.RecoScoring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인기 상품 분석 (Java/SQL only): sales .35, sales growth .15, cart adds .15, wishlist adds .10, unique views .15,
 * recency .10 over 7 or 30 days, each normalised by the best product. Views are counted once per viewer per
 * day (bots are never recorded). Ranked lists are precomputed hourly into {@code product_recommendation}.
 */
@Service
public class PopularityService {

    public static final List<String> LIST_TYPES = List.of("TOP10", "TOP_TOPS", "TOP_BOTTOMS", "NEW_POPULAR", "TRENDING");
    public static final List<Integer> PERIODS = List.of(7, 30);
    private static final Logger log = LoggerFactory.getLogger(PopularityService.class);
    private static final int LIST_SIZE = 30;

    private final JdbcTemplate jdbc;
    private final CatalogSnapshotService catalog;
    private final RecoSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<Integer, CachedStats> statsCache = new ConcurrentHashMap<>();

    public PopularityService(JdbcTemplate jdbc, CatalogSnapshotService catalog, RecoSettingsService settingsService,
                             ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public record Stats(Long productId, int sales, int previousSales, int cartAdds, int wishlistAdds, int views,
                        Instant lastSoldAt, double score, double trend, Map<String, Double> breakdown) {
    }

    public record RankedItem(int rank, Long productId, double score, String breakdown, Instant computedAt) {
    }

    private record CachedStats(Map<Long, Stats> stats, long at) {
    }

    /** Popularity score 0..1 per product for the period (cached for 10 minutes). */
    public Map<Long, Stats> stats(int periodDays) {
        CachedStats cachedStats = statsCache.get(periodDays);
        if (cachedStats != null && System.currentTimeMillis() - cachedStats.at() < 600_000) {
            return cachedStats.stats();
        }
        Map<Long, Stats> computed = compute(periodDays);
        statsCache.put(periodDays, new CachedStats(computed, System.currentTimeMillis()));
        return computed;
    }

    public double popularity(Long productId) {
        Stats s = stats(settingsService.settings().popularDays()).get(productId);
        return s == null ? 0 : s.score();
    }

    Map<Long, Stats> compute(int periodDays) {
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(periodDays));
        Instant prevFrom = from.minus(Duration.ofDays(periodDays));
        Map<Long, int[]> raw = new HashMap<>();
        Map<Long, Instant> lastSold = new HashMap<>();
        jdbc.query("""
                select oi.product_id,
                       coalesce(sum(case when pay.approved_at >= ? then oi.quantity else 0 end), 0),
                       coalesce(sum(case when pay.approved_at < ? then oi.quantity else 0 end), 0),
                       max(pay.approved_at)
                from order_item oi
                join (select order_id, min(approved_at) as approved_at from payment
                      where approved_at is not null group by order_id) pay on pay.order_id = oi.order_id
                where oi.status = 'ORDERED' and pay.approved_at >= ?
                group by oi.product_id""", rs -> {
            int[] r = raw.computeIfAbsent(rs.getLong(1), k -> new int[5]);
            r[0] = rs.getInt(2);
            r[1] = rs.getInt(3);
            Timestamp ts = rs.getTimestamp(4);
            if (ts != null) {
                lastSold.put(rs.getLong(1), ts.toInstant());
            }
        }, Timestamp.from(from), Timestamp.from(from), Timestamp.from(prevFrom));
        jdbc.query("""
                select product_id, count(*) from (
                    select s.product_id from cart_item ci join product_sku s on s.sku_id = ci.sku_id
                    where ci.created_at >= ?
                    union all
                    select product_id from recommendation_event where event_type = 'CART' and created_at >= ?
                        and product_id is not null and request_id is not null
                ) x group by product_id""", rs -> {
            raw.computeIfAbsent(rs.getLong(1), k -> new int[5])[2] = rs.getInt(2);
        }, Timestamp.from(from), Timestamp.from(from));
        jdbc.query("select product_id, count(*) from wishlist where created_at >= ? group by product_id", rs -> {
            raw.computeIfAbsent(rs.getLong(1), k -> new int[5])[3] = rs.getInt(2);
        }, Timestamp.from(from));
        jdbc.query("""
                select product_id, count(distinct coalesce('m' || member_id::text, 's' || session_key)
                                             || ':' || cast(viewed_at at time zone 'Asia/Seoul' as date))
                from product_view_history where viewed_at >= ? group by product_id""", rs -> {
            raw.computeIfAbsent(rs.getLong(1), k -> new int[5])[4] = rs.getInt(2);
        }, Timestamp.from(from));

        int maxSales = 1;
        int maxCart = 1;
        int maxWish = 1;
        int maxViews = 1;
        for (int[] r : raw.values()) {
            maxSales = Math.max(maxSales, r[0]);
            maxCart = Math.max(maxCart, r[2]);
            maxWish = Math.max(maxWish, r[3]);
            maxViews = Math.max(maxViews, r[4]);
        }
        Map<Long, Stats> result = new HashMap<>();
        for (Map.Entry<Long, int[]> entry : raw.entrySet()) {
            int[] r = entry.getValue();
            double sales = (double) r[0] / maxSales;
            double growth = r[0] == 0 ? 0 : RecoScoring.clamp((double) (r[0] - r[1]) / Math.max(r[1], 1) / 3.0);
            double cart = (double) r[2] / maxCart;
            double wish = (double) r[3] / maxWish;
            double views = (double) r[4] / maxViews;
            Instant last = lastSold.get(entry.getKey());
            double recency = last == null || last.isBefore(from) ? 0
                    : RecoScoring.clamp(1 - Duration.between(last, now).toHours() / (24.0 * periodDays));
            double score = sales * .35 + growth * .15 + cart * .15 + wish * .10 + views * .15 + recency * .10;
            double trend = growth * .5 + cart * .25 + views * .25;
            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put("sales", RecoScoring.round(sales));
            breakdown.put("growth", RecoScoring.round(growth));
            breakdown.put("cartAdds", RecoScoring.round(cart));
            breakdown.put("wishlist", RecoScoring.round(wish));
            breakdown.put("views", RecoScoring.round(views));
            breakdown.put("recency", RecoScoring.round(recency));
            result.put(entry.getKey(), new Stats(entry.getKey(), r[0], r[1], r[2], r[3], r[4], last,
                    RecoScoring.round(score), RecoScoring.round(trend), breakdown));
        }
        return result;
    }

    /** Hourly: recompute every list for 7 and 30 days. */
    @Scheduled(cron = "${app.reco.popular-cron:0 5 * * * *}")
    public void scheduledRefresh() {
        try {
            refreshAll();
        } catch (RuntimeException ex) {
            log.warn("Popular list refresh failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public void refreshAll() {
        statsCache.clear();
        catalog.invalidate();
        for (int period : PERIODS) {
            Map<Long, Stats> stats = stats(period);
            for (String listType : LIST_TYPES) {
                store(listType, period, rank(listType, period, stats));
            }
        }
    }

    List<RecoScoring.Scored> rank(String listType, int period, Map<Long, Stats> stats) {
        RecoSettingsService.Settings settings = settingsService.settings();
        Set<Long> excluded = settingsService.excludedProducts();
        Instant now = clock.instant();
        List<RecoScoring.Scored> scored = new ArrayList<>();
        for (ProductProfile p : CatalogSnapshotService.eligibleList(catalog.snapshot(), settings.minStock(), excluded)) {
            Stats s = stats.get(p.productId());
            double fresh = RecoScoring.freshness(p.createdAt(), now);
            Map<String, Double> breakdown = s == null ? new LinkedHashMap<>() : new LinkedHashMap<>(s.breakdown());
            double score = s == null ? 0 : s.score();
            switch (listType) {
                case "TOP_TOPS" -> {
                    if (!ProductTraits.TOP.equals(p.group())) {
                        continue;
                    }
                }
                case "TOP_BOTTOMS" -> {
                    if (!ProductTraits.BOTTOM.equals(p.group())) {
                        continue;
                    }
                }
                case "NEW_POPULAR" -> {
                    if (Duration.between(p.createdAt(), now).toDays() > 30) {
                        continue;
                    }
                    score = score * 0.7 + fresh * 0.3;
                    breakdown.put("freshness", RecoScoring.round(fresh));
                }
                case "TRENDING" -> score = s == null ? 0 : s.trend();
                default -> {
                }
            }
            // ties (e.g. a new shop without sales) fall back to newest first
            double tieBreak = fresh * 0.0001;
            scored.add(new RecoScoring.Scored(p.productId(), RecoScoring.round(score) + tieBreak, breakdown,
                    s == null ? "신상품 우선(판매 데이터 없음)" : "최근 " + period + "일 판매 " + s.sales() + "개, 조회 "
                            + s.views() + "회, 장바구니 " + s.cartAdds() + "회"));
        }
        scored.sort(Comparator.comparingDouble(RecoScoring.Scored::score).reversed());
        return scored.size() > LIST_SIZE ? scored.subList(0, LIST_SIZE) : scored;
    }

    private void store(String listType, int period, List<RecoScoring.Scored> ranked) {
        jdbc.update("delete from product_recommendation where list_type = ? and period_days = ?", listType, period);
        Timestamp now = Timestamp.from(clock.instant());
        int rank = 1;
        for (RecoScoring.Scored item : ranked) {
            Map<String, Object> payload = new LinkedHashMap<>(item.breakdown());
            payload.put("reason", item.reason());
            jdbc.update("""
                    insert into product_recommendation (list_type, period_days, rank_no, product_id, score, breakdown,
                                                        computed_at) values (?, ?, ?, ?, ?, ?, ?)""",
                    listType, period, rank++, item.productId(), Math.min(item.score(), 9999), json(payload), now);
        }
    }

    /** Stored ranking; computed on the fly the first time (e.g. right after deployment). */
    public List<RankedItem> list(String listType, int period) {
        String type = LIST_TYPES.contains(listType) ? listType : "TOP10";
        int days = PERIODS.contains(period) ? period : 7;
        List<RankedItem> items = load(type, days);
        if (items.isEmpty()) {
            refreshAll();
            items = load(type, days);
        }
        return items;
    }

    private List<RankedItem> load(String listType, int period) {
        return jdbc.query("""
                select rank_no, product_id, score, breakdown, computed_at from product_recommendation
                where list_type = ? and period_days = ? order by rank_no""", (rs, i) -> new RankedItem(
                rs.getInt(1), rs.getLong(2), rs.getDouble(3), rs.getString(4), rs.getTimestamp(5).toInstant()),
                listType, period);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
