package com.petitcamel.shop.reco.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.reco.support.ProductProfile;
import com.petitcamel.shop.reco.support.RecoScoring;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a viewer's taste profile from their own behaviour only: product views (1), wishlist (3), cart (3),
 * purchases (5) and recent searches. Members who switched personalization off get an empty profile. Member
 * profiles are saved to {@code customer_product_preference} for the admin overview.
 */
@Service
public class PreferenceService {

    private static final double W_VIEW = 1;
    private static final double W_WISH = 3;
    private static final double W_CART = 3;
    private static final double W_PURCHASE = 5;

    private final JdbcTemplate jdbc;
    private final CatalogSnapshotService catalog;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PreferenceService(JdbcTemplate jdbc, CatalogSnapshotService catalog, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public record Signals(RecoScoring.Preference preference, List<Long> anchors, Set<Long> purchased,
                          Set<Long> inCart, List<Long> recentViews) {
        static Signals empty() {
            return new Signals(new RecoScoring.Preference(Map.of(), Map.of(), Map.of(), Map.of(), null, null, null,
                    Set.of(), 0), List.of(), Set.of(), Set.of(), List.of());
        }
    }

    public Signals signals(Viewer viewer) {
        if (viewer.anonymous() || (viewer.member() && !viewer.personalizationAllowed())) {
            Signals empty = Signals.empty();
            if (viewer.member()) {
                return new Signals(empty.preference(), List.of(), Set.of(), cartProducts(viewer.memberId()), List.of());
            }
            return empty;
        }
        Instant now = clock.instant();
        Instant viewsFrom = now.minus(Duration.ofDays(30));
        List<Long> views = viewer.member()
                ? jdbc.queryForList("""
                        select product_id from product_view_history where member_id = ? and viewed_at >= ?
                        order by viewed_at desc limit 200""", Long.class, viewer.memberId(), Timestamp.from(viewsFrom))
                : jdbc.queryForList("""
                        select product_id from product_view_history where session_key = ? and viewed_at >= ?
                        order by viewed_at desc limit 200""", Long.class, viewer.sessionKey(), Timestamp.from(viewsFrom));
        List<String> searches = viewer.member()
                ? jdbc.queryForList("select keyword from customer_search_history where member_id = ? "
                + "order by searched_at desc limit 10", String.class, viewer.memberId())
                : jdbc.queryForList("select keyword from customer_search_history where session_key = ? "
                + "order by searched_at desc limit 10", String.class, viewer.sessionKey());
        List<Long> wishlist = List.of();
        Set<Long> cart = Set.of();
        List<Long> purchases = List.of();
        if (viewer.member()) {
            wishlist = jdbc.queryForList("select product_id from wishlist where member_id = ? "
                    + "order by created_at desc limit 100", Long.class, viewer.memberId());
            cart = cartProducts(viewer.memberId());
            purchases = jdbc.queryForList("""
                    select oi.product_id from order_item oi join orders o on o.order_id = oi.order_id
                    where o.member_id = ? and oi.status = 'ORDERED' and o.ordered_at >= ?
                      and exists (select 1 from payment p where p.order_id = o.order_id and p.approved_at is not null)
                    group by oi.product_id order by max(o.ordered_at) desc limit 100""", Long.class,
                    viewer.memberId(), Timestamp.from(now.minus(Duration.ofDays(365))));
        }

        Map<Long, Double> categories = new HashMap<>();
        Map<String, Double> groups = new HashMap<>();
        Map<String, Double> colors = new HashMap<>();
        Map<String, Double> styles = new HashMap<>();
        double priceWeight = 0;
        double priceSum = 0;
        Double priceMin = null;
        Double priceMax = null;
        int signalCount = 0;
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        List<Object[]> weighted = new ArrayList<>();
        views.forEach(id -> weighted.add(new Object[]{id, W_VIEW}));
        wishlist.forEach(id -> weighted.add(new Object[]{id, W_WISH}));
        cart.forEach(id -> weighted.add(new Object[]{id, W_CART}));
        purchases.forEach(id -> weighted.add(new Object[]{id, W_PURCHASE}));
        for (Object[] entry : weighted) {
            ProductProfile p = snapshot.get((Long) entry[0]);
            if (p == null) {
                continue;
            }
            double w = (double) entry[1];
            signalCount++;
            categories.merge(p.categoryId(), w, Double::sum);
            groups.merge(p.group(), w, Double::sum);
            p.colorFamilies().forEach(c -> colors.merge(c, w, Double::sum));
            p.styles().forEach(s -> styles.merge(s, w, Double::sum));
            priceSum += p.price() * w;
            priceWeight += w;
            priceMin = priceMin == null ? p.price() : Math.min(priceMin, p.price());
            priceMax = priceMax == null ? p.price() : Math.max(priceMax, p.price());
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String keyword : searches) {
            String k = keyword.trim().toLowerCase();
            if (k.length() >= 2) {
                keywords.add(k);
            }
        }
        RecoScoring.Preference preference = new RecoScoring.Preference(normalize(categories), normalize(groups),
                normalize(colors), normalize(styles), priceWeight == 0 ? null : priceSum / priceWeight, priceMin,
                priceMax, keywords, signalCount + keywords.size());

        List<Long> anchors = new ArrayList<>();
        purchases.stream().limit(5).forEach(anchors::add);
        cart.stream().filter(id -> !anchors.contains(id)).limit(3).forEach(anchors::add);
        wishlist.stream().filter(id -> !anchors.contains(id)).limit(3).forEach(anchors::add);
        List<Long> recent = views.stream().distinct().limit(10).toList();
        recent.stream().filter(id -> !anchors.contains(id)).limit(3).forEach(anchors::add);

        if (viewer.member() && preference.signalCount() > 0) {
            save(viewer.memberId(), preference);
        }
        return new Signals(preference, anchors, new LinkedHashSet<>(purchases), cart, recent);
    }

    private Set<Long> cartProducts(Long memberId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                select distinct s.product_id from cart c join cart_item ci on ci.cart_id = c.cart_id
                join product_sku s on s.sku_id = ci.sku_id where c.member_id = ?""", Long.class, memberId));
    }

    private void save(Long memberId, RecoScoring.Preference p) {
        String keywords = String.join(",", p.keywords());
        jdbc.update("""
                insert into customer_product_preference (member_id, category_scores, color_scores, style_scores,
                    price_min, price_max, price_avg, recent_keywords, signal_count, computed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (member_id) do update set category_scores = excluded.category_scores,
                    color_scores = excluded.color_scores, style_scores = excluded.style_scores,
                    price_min = excluded.price_min, price_max = excluded.price_max, price_avg = excluded.price_avg,
                    recent_keywords = excluded.recent_keywords, signal_count = excluded.signal_count,
                    computed_at = excluded.computed_at""",
                memberId, json(p.categories()), json(p.colors()), json(p.styles()), p.priceMin(), p.priceMax(),
                p.priceAvg(), keywords.length() > 500 ? keywords.substring(0, 500) : keywords, p.signalCount(),
                Timestamp.from(clock.instant()));
    }

    private static <K> Map<K, Double> normalize(Map<K, Double> raw) {
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<K, Double> result = new LinkedHashMap<>();
        if (max <= 0) {
            return result;
        }
        raw.forEach((k, v) -> result.put(k, RecoScoring.round(v / max)));
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
