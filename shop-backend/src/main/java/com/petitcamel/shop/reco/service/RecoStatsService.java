package com.petitcamel.shop.reco.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추천 성과: 결제 완료된 주문 라인을 같은 회원(또는 그 회원과 연결된 비회원 세션)의 추천 클릭과
 * 기여 기간 안에서 연결해 PURCHASE 이벤트를 만들고(주문 라인당 1회), 일별 집계를 갱신합니다.
 * 추천 매출과 전체 매출은 분리해서 보여 줍니다.
 */
@Service
public class RecoStatsService {

    private static final Logger log = LoggerFactory.getLogger(RecoStatsService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final RecoSettingsService settingsService;
    private final Clock clock;

    public RecoStatsService(JdbcTemplate jdbc, RecoSettingsService settingsService, Clock clock) {
        this.jdbc = jdbc;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    public record PlacementStats(String placement, String variant, long impressions, long clicks, long carts,
                                 long purchases, BigDecimal revenue, double ctr, double cartRate,
                                 double conversionRate) {
    }

    public record DailyPoint(LocalDate date, long impressions, long clicks, long carts, long purchases,
                             BigDecimal revenue) {
    }

    public record Analytics(LocalDate from, LocalDate to, int attributionDays, List<PlacementStats> placements,
                            List<DailyPoint> daily, long impressions, long clicks, long carts, long purchases,
                            BigDecimal recommendationRevenue, BigDecimal totalRevenue,
                            BigDecimal normalRevenue, double revenueContribution, List<String> notes) {
    }

    /** 30분마다: 기여 기간+1일 범위의 구매를 연결하고 오늘·어제 집계를 갱신합니다. */
    @Scheduled(cron = "${app.reco.stats-cron:0 */30 * * * *}")
    public void scheduled() {
        try {
            LocalDate today = LocalDate.now(clock.withZone(ZONE));
            attribute(today.minusDays(settingsService.settings().attributionDays() + 1L), today);
            refreshDaily(today.minusDays(1));
            refreshDaily(today);
        } catch (RuntimeException ex) {
            log.warn("Recommendation stats refresh failed: {}", ex.getMessage());
        }
    }

    /** 결제일이 [from, to] 인 주문 라인의 추천 기여를 기록합니다. 같은 라인은 한 번만 기록됩니다. */
    public int attribute(LocalDate from, LocalDate to) {
        int days = settingsService.settings().attributionDays();
        return jdbc.update("""
                insert into recommendation_event (product_id, event_type, created_at, request_id, placement, variant,
                                                  member_id, session_key, order_item_id, revenue, dedupe_key)
                select oi.product_id, 'PURCHASE', pay.approved_at, c.request_id, c.placement, c.variant, o.member_id,
                       c.session_key, oi.order_item_id, oi.payment_price, 'pur:' || oi.order_item_id
                from order_item oi
                join orders o on o.order_id = oi.order_id
                join (select order_id, min(approved_at) as approved_at from payment
                      where approved_at is not null group by order_id) pay on pay.order_id = oi.order_id
                join lateral (
                    select e.request_id, e.placement, e.variant, e.session_key
                    from recommendation_event e
                    where e.event_type = 'CLICK' and e.request_id is not null and e.product_id = oi.product_id
                      and e.created_at <= pay.approved_at
                      and e.created_at >= pay.approved_at - make_interval(days => ?)
                      and (e.member_id = o.member_id or e.session_key in (
                            select v.session_key from product_view_history v
                            where v.member_id = o.member_id and v.session_key is not null
                            union
                            select r.session_key from recommendation_event r
                            where r.member_id = o.member_id and r.session_key is not null))
                    order by e.created_at desc limit 1) c on true
                where oi.status = 'ORDERED' and pay.approved_at >= ? and pay.approved_at < ?
                on conflict (dedupe_key) where dedupe_key is not null do nothing""",
                days, start(from), start(to.plusDays(1)));
    }

    public void refreshDaily(LocalDate day) {
        jdbc.update("delete from recommendation_daily_stats where stat_date = ?", Date.valueOf(day));
        jdbc.update("""
                insert into recommendation_daily_stats (stat_date, placement, variant, impressions, clicks, carts,
                                                        purchases, revenue, computed_at)
                select ?, e.placement, coalesce(e.variant, 'A'),
                       count(*) filter (where e.event_type = 'IMPRESSION'),
                       count(*) filter (where e.event_type = 'CLICK'),
                       count(*) filter (where e.event_type = 'CART'),
                       count(*) filter (where e.event_type = 'PURCHASE' and oi.status = 'ORDERED'),
                       coalesce(sum(e.revenue) filter (where e.event_type = 'PURCHASE' and oi.status = 'ORDERED'), 0),
                       ?
                from recommendation_event e
                left join order_item oi on oi.order_item_id = e.order_item_id
                where e.request_id is not null and e.placement is not null
                  and e.created_at >= ? and e.created_at < ?
                group by e.placement, coalesce(e.variant, 'A')""",
                Date.valueOf(day), Timestamp.from(clock.instant()), start(day), start(day.plusDays(1)));
    }

    public Analytics analytics(int days) {
        int span = Math.max(1, Math.min(days, 180));
        LocalDate to = LocalDate.now(clock.withZone(ZONE));
        LocalDate from = to.minusDays(span - 1L);
        attribute(from.minusDays(settingsService.settings().attributionDays()), to);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            refreshDaily(d);
        }
        Map<String, PlacementStats> byPlacement = new LinkedHashMap<>();
        jdbc.query("""
                select placement, variant, sum(impressions), sum(clicks), sum(carts), sum(purchases), sum(revenue)
                from recommendation_daily_stats where stat_date between ? and ?
                group by placement, variant order by placement, variant""", rs -> {
            long imp = rs.getLong(3);
            long clk = rs.getLong(4);
            long cart = rs.getLong(5);
            long pur = rs.getLong(6);
            byPlacement.put(rs.getString(1) + ":" + rs.getString(2), new PlacementStats(rs.getString(1),
                    rs.getString(2), imp, clk, cart, pur, rs.getBigDecimal(7), rate(clk, imp), rate(cart, clk),
                    rate(pur, clk)));
        }, Date.valueOf(from), Date.valueOf(to));
        List<DailyPoint> daily = jdbc.query("""
                select stat_date, sum(impressions), sum(clicks), sum(carts), sum(purchases), sum(revenue)
                from recommendation_daily_stats where stat_date between ? and ?
                group by stat_date order by stat_date""", (rs, i) -> new DailyPoint(rs.getDate(1).toLocalDate(),
                rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getBigDecimal(6)),
                Date.valueOf(from), Date.valueOf(to));
        BigDecimal total = jdbc.queryForObject("""
                select coalesce(sum(oi.payment_price), 0) from order_item oi
                join (select order_id, min(approved_at) as approved_at from payment
                      where approved_at is not null group by order_id) pay on pay.order_id = oi.order_id
                where oi.status = 'ORDERED' and pay.approved_at >= ? and pay.approved_at < ?""",
                BigDecimal.class, start(from), start(to.plusDays(1)));
        List<PlacementStats> placements = new ArrayList<>(byPlacement.values());
        long imp = placements.stream().mapToLong(PlacementStats::impressions).sum();
        long clk = placements.stream().mapToLong(PlacementStats::clicks).sum();
        long cart = placements.stream().mapToLong(PlacementStats::carts).sum();
        long pur = placements.stream().mapToLong(PlacementStats::purchases).sum();
        BigDecimal recoRevenue = placements.stream().map(PlacementStats::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevenue = total == null ? BigDecimal.ZERO : total;
        double contribution = totalRevenue.signum() == 0 ? 0
                : recoRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue();
        int attributionDays = settingsService.settings().attributionDays();
        return new Analytics(from, to, attributionDays, placements, daily, imp, clk, cart, pur, recoRevenue,
                totalRevenue, totalRevenue.subtract(recoRevenue).max(BigDecimal.ZERO), contribution, List.of(
                "CTR = 클릭 ÷ 노출, 장바구니율 = 장바구니 ÷ 클릭, 구매 전환율 = 구매 ÷ 클릭",
                "구매 기여: 추천 클릭 후 " + attributionDays + "일 이내 같은 회원이 결제한 같은 상품만 집계(주문 라인당 1회)",
                "취소·반품된 주문 라인은 추천 매출에서 제외",
                "노출은 추천 목록 응답 1건당 1회, 같은 이벤트는 중복 집계하지 않음",
                "변형(variant) 열은 A/B 테스트용이며 현재는 모두 A"));
    }

    private static Timestamp start(LocalDate day) {
        return Timestamp.from(day.atStartOfDay(ZONE).toInstant());
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round((double) numerator / denominator * 10000) / 10000.0;
    }
}
