package com.petitcamel.shop.reco.service;

import com.petitcamel.shop.product.dto.ProductSummaryResponse;
import com.petitcamel.shop.product.service.ProductCatalogService;
import com.petitcamel.shop.reco.support.ProductProfile;
import com.petitcamel.shop.reco.support.ProductTraits;
import com.petitcamel.shop.reco.support.RecoScoring;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Customer-facing recommendation lists. Everything is computed in Java from the catalog snapshot, popularity
 * stats and the viewer's own behaviour — no AI API is called, so lists keep working with AI disabled. Sold-out,
 * suspended (HIDDEN/DRAFT), discontinued and admin-excluded products are never returned.
 */
@Service
public class RecommendationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final CatalogSnapshotService catalog;
    private final PopularityService popularity;
    private final PreferenceService preferences;
    private final RecoSettingsService settingsService;
    private final RecoTrackingService tracking;
    private final ProductCatalogService productCatalog;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RecommendationService(CatalogSnapshotService catalog, PopularityService popularity,
                                 PreferenceService preferences, RecoSettingsService settingsService,
                                 RecoTrackingService tracking, ProductCatalogService productCatalog, Clock clock) {
        this.catalog = catalog;
        this.popularity = popularity;
        this.preferences = preferences;
        this.settingsService = settingsService;
        this.tracking = tracking;
        this.productCatalog = productCatalog;
        this.clock = clock;
    }

    public record RecoItem(ProductSummaryResponse product, String reason) {
    }

    public record RecoList(String requestId, String placement, String title, String algorithm,
                           List<RecoItem> items) {
    }

    /** Scored list before product cards are attached (also used by the admin preview). */
    public record Computed(String algorithm, List<RecoScoring.Scored> items) {
    }

    private record CacheEntry(Computed value, long expiresAt) {
    }

    // ------------------------------------------------------------------ public placements

    public RecoList personalized(Viewer viewer, String placement, Integer limit) {
        return respond(viewer, placement, titleFor(placement), limit, () -> computePersonalized(viewer, size(limit)),
                "personal:" + viewer.cacheKey());
    }

    public RecoList similar(Viewer viewer, Long productId, Integer limit) {
        return respond(viewer, "DETAIL_SIMILAR", "이 상품과 비슷한 상품", limit,
                () -> computeSimilar(productId, size(limit)), "similar:" + productId);
    }

    public RecoList coordination(Viewer viewer, Long productId, Integer limit) {
        return respond(viewer, "DETAIL_COORDI", "함께 코디하기 좋은 상품", limit,
                () -> computeCoordination(List.of(productId), Set.of(productId), size(limit)), "coordi:" + productId);
    }

    public RecoList similarPrice(Viewer viewer, Long productId, Integer limit) {
        return respond(viewer, "DETAIL_PRICE", "비슷한 가격대 상품", limit,
                () -> computeSimilarPrice(productId, size(limit)), "price:" + productId);
    }

    public RecoList popular(Viewer viewer, String listType, Integer period, Integer limit) {
        String type = listType == null ? "TOP10" : listType.toUpperCase();
        int days = period == null ? settingsService.settings().popularDays() : period;
        return respond(viewer, "HOME_POPULAR", popularTitle(type), limit,
                () -> computePopular(type, days, size(limit)), "popular:" + type + ":" + days);
    }

    public RecoList newArrivals(Viewer viewer, Integer limit) {
        return respond(viewer, "HOME_NEW", "새로 들어온 상품", limit, () -> computeNew(size(limit)), "new");
    }

    public RecoList cart(Viewer viewer, Integer limit) {
        PreferenceService.Signals signals = preferences.signals(viewer);
        Set<Long> inCart = signals.inCart();
        return respond(viewer, "CART_MATCH", "장바구니 상품과 잘 어울리는 상품", limit, () -> {
            if (inCart.isEmpty()) {
                return computePopular("TOP10", settingsService.settings().popularDays(), size(limit));
            }
            Computed coordi = computeCoordination(List.copyOf(inCart), inCart, size(limit));
            if (!coordi.items().isEmpty()) {
                return coordi;
            }
            return computeSimilarMany(List.copyOf(inCart), inCart, size(limit));
        }, "cart:" + viewer.cacheKey() + ":" + inCart.hashCode());
    }

    public RecoList recentSimilar(Viewer viewer, Integer limit) {
        PreferenceService.Signals signals = preferences.signals(viewer);
        List<Long> recent = signals.recentViews().stream().limit(5).toList();
        return respond(viewer, "MYPAGE_RECENT", "최근 본 상품과 비슷한 상품", limit, () -> recent.isEmpty()
                        ? computeNew(size(limit))
                        : computeSimilarMany(recent, new LinkedHashSet<>(recent), size(limit)),
                "recent:" + viewer.cacheKey() + ":" + recent.hashCode());
    }

    // ------------------------------------------------------------------ computations (also used by admin preview)

    public Computed computePersonalized(Viewer viewer, int limit) {
        PreferenceService.Signals signals = preferences.signals(viewer);
        RecoSettingsService.Settings settings = settingsService.settings();
        if (signals.preference().empty()) {
            return popularPlusNew(limit, signals.inCart());
        }
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        Set<Long> excluded = settingsService.excludedProducts();
        Set<Long> skip = new LinkedHashSet<>(signals.purchased());
        skip.addAll(signals.inCart());
        List<ProductProfile> anchors = signals.anchors().stream().map(snapshot::get).filter(p -> p != null).toList();
        Map<Long, PopularityService.Stats> stats = popularity.stats(settings.popularDays());
        Instant now = clock.instant();
        List<RecoScoring.Scored> scored = new ArrayList<>();
        for (ProductProfile p : CatalogSnapshotService.eligibleList(snapshot, settings.minStock(), excluded)) {
            if (skip.contains(p.productId())) {
                continue;
            }
            double purchaseSimilarity = 0;
            for (ProductProfile anchor : anchors) {
                if (!anchor.productId().equals(p.productId())) {
                    purchaseSimilarity = Math.max(purchaseSimilarity, RecoScoring.similar(anchor, p).score());
                }
            }
            PopularityService.Stats s = stats.get(p.productId());
            scored.add(RecoScoring.personal(p, signals.preference(), purchaseSimilarity, s == null ? 0 : s.score(),
                    settings.weights(), now, boost(p)));
        }
        return new Computed("PERSONALIZED", diversify(scored, limit, snapshot));
    }

    public Computed computeSimilar(Long productId, int limit) {
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        ProductProfile base = snapshot.get(productId);
        if (base == null) {
            return new Computed("SIMILAR", List.of());
        }
        return computeSimilarMany(List.of(productId), Set.of(productId), limit);
    }

    Computed computeSimilarMany(List<Long> baseIds, Set<Long> skip, int limit) {
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        RecoSettingsService.Settings settings = settingsService.settings();
        Set<Long> excluded = settingsService.excludedProducts();
        List<ProductProfile> bases = baseIds.stream().map(snapshot::get).filter(p -> p != null).toList();
        Map<Long, RecoScoring.Scored> best = new HashMap<>();
        for (ProductProfile p : CatalogSnapshotService.eligibleList(snapshot, settings.minStock(), excluded)) {
            if (skip.contains(p.productId())) {
                continue;
            }
            for (ProductProfile base : bases) {
                RecoScoring.Scored s = RecoScoring.similar(base, p);
                if (s.score() >= 0.15) {
                    best.merge(p.productId(), s, (a, b) -> a.score() >= b.score() ? a : b);
                }
            }
        }
        List<RecoScoring.Scored> list = new ArrayList<>(best.values());
        list.sort(Comparator.comparingDouble(RecoScoring.Scored::score).reversed());
        return new Computed("SIMILAR", list.stream().limit(limit).toList());
    }

    public Computed computeCoordination(List<Long> baseIds, Set<Long> skip, int limit) {
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        RecoSettingsService.Settings settings = settingsService.settings();
        Set<Long> excluded = settingsService.excludedProducts();
        List<RecoSettingsService.Rule> rules = settingsService.activeRules("COORDINATION");
        String season = ProductTraits.seasonOf(clock.instant().atZone(ZONE).getMonth());
        List<ProductProfile> bases = baseIds.stream().map(snapshot::get).filter(p -> p != null).toList();
        Map<Long, RecoScoring.Scored> best = new HashMap<>();
        for (ProductProfile p : CatalogSnapshotService.eligibleList(snapshot, settings.minStock(), excluded)) {
            if (skip.contains(p.productId())) {
                continue;
            }
            for (ProductProfile base : bases) {
                RecoScoring.Scored s = RecoScoring.coordination(base, p, ruleWeight(rules, base, p), season);
                if (s != null) {
                    double boosted = RecoScoring.round(s.score() * boost(p));
                    RecoScoring.Scored adjusted = new RecoScoring.Scored(s.productId(), boosted, s.breakdown(), s.reason());
                    best.merge(p.productId(), adjusted, (a, b) -> a.score() >= b.score() ? a : b);
                }
            }
        }
        List<RecoScoring.Scored> list = new ArrayList<>(best.values());
        list.sort(Comparator.comparingDouble(RecoScoring.Scored::score).reversed());
        return new Computed("COORDINATION", diversify(list, limit, snapshot));
    }

    public Computed computeSimilarPrice(Long productId, int limit) {
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        ProductProfile base = snapshot.get(productId);
        if (base == null) {
            return new Computed("SIMILAR_PRICE", List.of());
        }
        RecoSettingsService.Settings settings = settingsService.settings();
        Map<Long, PopularityService.Stats> stats = popularity.stats(settings.popularDays());
        List<RecoScoring.Scored> list = new ArrayList<>();
        for (ProductProfile p : CatalogSnapshotService.eligibleList(snapshot, settings.minStock(),
                settingsService.excludedProducts())) {
            if (p.productId().equals(productId) || !p.group().equals(base.group())) {
                continue;
            }
            double price = RecoScoring.priceSimilarity(base.price(), p.price());
            if (price < 0.75) {
                continue;
            }
            PopularityService.Stats s = stats.get(p.productId());
            double pop = s == null ? 0 : s.score();
            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put("price", RecoScoring.round(price));
            breakdown.put("popularity", RecoScoring.round(pop));
            list.add(new RecoScoring.Scored(p.productId(), RecoScoring.round(price * 0.7 + pop * 0.3), breakdown,
                    "비슷한 가격대예요"));
        }
        list.sort(Comparator.comparingDouble(RecoScoring.Scored::score).reversed());
        return new Computed("SIMILAR_PRICE", list.stream().limit(limit).toList());
    }

    public Computed computePopular(String listType, int period, int limit) {
        RecoSettingsService.Settings settings = settingsService.settings();
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        Set<Long> excluded = settingsService.excludedProducts();
        List<RecoScoring.Scored> list = new ArrayList<>();
        for (PopularityService.RankedItem item : popularity.list(listType, period)) {
            if (!CatalogSnapshotService.eligible(snapshot.get(item.productId()), settings.minStock(), excluded)) {
                continue;
            }
            list.add(new RecoScoring.Scored(item.productId(), RecoScoring.round(item.score()), Map.of(),
                    "TOP10".equals(listType) ? "지금 인기 있는 상품이에요" : popularTitle(listType)));
            if (list.size() >= limit) {
                break;
            }
        }
        return new Computed("POPULAR_" + listType, list);
    }

    public Computed computeNew(int limit) {
        RecoSettingsService.Settings settings = settingsService.settings();
        Instant now = clock.instant();
        List<RecoScoring.Scored> list = CatalogSnapshotService.eligibleList(catalog.snapshot(), settings.minStock(),
                        settingsService.excludedProducts()).stream()
                .sorted(Comparator.comparing(ProductProfile::createdAt).reversed())
                .limit(limit)
                .map(p -> new RecoScoring.Scored(p.productId(), RecoScoring.freshness(p.createdAt(), now),
                        Map.of("freshness", RecoScoring.freshness(p.createdAt(), now)), "새로 들어온 상품이에요"))
                .toList();
        return new Computed("NEW_ARRIVALS", list);
    }

    /** New customers / no signals: popular and new products interleaved. */
    Computed popularPlusNew(int limit, Set<Long> skip) {
        List<RecoScoring.Scored> popularItems = computePopular("TOP10", settingsService.settings().popularDays(),
                limit * 2).items();
        List<RecoScoring.Scored> newItems = computeNew(limit * 2).items();
        List<RecoScoring.Scored> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>(skip);
        int i = 0;
        int j = 0;
        while (result.size() < limit && (i < popularItems.size() || j < newItems.size())) {
            if (i < popularItems.size() && (result.size() % 2 == 0 || j >= newItems.size())) {
                RecoScoring.Scored s = popularItems.get(i++);
                if (seen.add(s.productId())) {
                    result.add(s);
                }
            } else if (j < newItems.size()) {
                RecoScoring.Scored s = newItems.get(j++);
                if (seen.add(s.productId())) {
                    result.add(s);
                }
            }
        }
        return new Computed("POPULAR_AND_NEW", result);
    }

    // ------------------------------------------------------------------ helpers

    /** Best active coordination rule between two products, in either direction. Item-type rules beat groups. */
    static double ruleWeight(List<RecoSettingsService.Rule> rules, ProductProfile a, ProductProfile b) {
        double best = 0;
        for (RecoSettingsService.Rule rule : rules) {
            boolean forward = matches(rule.sourceValue(), a) && matches(rule.targetValue(), b);
            boolean backward = matches(rule.sourceValue(), b) && matches(rule.targetValue(), a);
            if (forward || backward) {
                best = Math.max(best, rule.weight().doubleValue());
            }
        }
        return best;
    }

    static boolean matches(String value, ProductProfile p) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (v.equalsIgnoreCase(p.group())) {
            return true;
        }
        if (v.equals(p.itemType()) || v.equals(p.categoryName())) {
            return true;
        }
        String canonical = ProductTraits.itemType(v);
        return canonical != null && canonical.equals(p.itemType());
    }

    private double boost(ProductProfile p) {
        double boost = 1.0;
        for (RecoSettingsService.Rule rule : settingsService.activeRules("CATEGORY_BOOST")) {
            if (matches(rule.sourceValue(), p)) {
                boost = Math.max(boost, rule.weight().doubleValue());
            }
        }
        return boost;
    }

    /** Highest scores first, at most half of the list from one category. */
    static List<RecoScoring.Scored> diversify(List<RecoScoring.Scored> scored, int limit,
                                              CatalogSnapshotService.Snapshot snapshot) {
        List<RecoScoring.Scored> sorted = new ArrayList<>(scored);
        sorted.sort(Comparator.comparingDouble(RecoScoring.Scored::score).reversed());
        int perCategory = Math.max(2, (limit + 1) / 2);
        Map<Long, Integer> counts = new HashMap<>();
        List<RecoScoring.Scored> result = new ArrayList<>();
        List<RecoScoring.Scored> overflow = new ArrayList<>();
        for (RecoScoring.Scored s : sorted) {
            ProductProfile p = snapshot.get(s.productId());
            Long category = p == null ? -1L : p.categoryId();
            if (counts.getOrDefault(category, 0) < perCategory) {
                counts.merge(category, 1, Integer::sum);
                result.add(s);
            } else {
                overflow.add(s);
            }
            if (result.size() >= limit) {
                return result;
            }
        }
        for (RecoScoring.Scored s : overflow) {
            if (result.size() >= limit) {
                break;
            }
            result.add(s);
        }
        return result;
    }

    private RecoList respond(Viewer viewer, String placement, String title, Integer limit, Supplier<Computed> compute,
                             String cacheKey) {
        RecoSettingsService.Settings settings = settingsService.settings();
        if (!settings.placementEnabled(placement)) {
            return new RecoList(null, placement, title, "DISABLED", List.of());
        }
        int size = size(limit);
        Computed computed = cached(cacheKey + ":" + size, settings.cacheMinutes(), compute);
        List<Long> ids = computed.items().stream().map(RecoScoring.Scored::productId).toList();
        Map<Long, String> reasons = new LinkedHashMap<>();
        computed.items().forEach(s -> reasons.put(s.productId(), s.reason()));
        List<RecoItem> items = productCatalog.getSummariesByProductIds(ids, false).stream()
                .filter(p -> !p.soldOut())
                .map(p -> new RecoItem(p, reasons.get(p.productId())))
                .toList();
        String requestId = items.isEmpty() ? null : tracking.issueRequestId(placement, tracking.variantFor(viewer));
        return new RecoList(requestId, placement, title, computed.algorithm(), items);
    }

    private Computed cached(String key, int minutes, Supplier<Computed> compute) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt() > now) {
            return entry.value();
        }
        Computed value = compute.get();
        if (minutes > 0) {
            if (cache.size() > 5000) {
                cache.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
                if (cache.size() > 5000) {
                    cache.clear();
                }
            }
            cache.put(key, new CacheEntry(value, now + Duration.ofMinutes(minutes).toMillis()));
        }
        return value;
    }

    public void clearCache() {
        cache.clear();
        catalog.invalidate();
    }

    private int size(Integer limit) {
        int display = settingsService.settings().displayCount();
        return limit == null || limit <= 0 ? display : Math.min(limit, 30);
    }

    private static String titleFor(String placement) {
        return switch (placement) {
            case "MYPAGE_TASTE" -> "취향 기반 추천";
            default -> "회원님을 위한 추천";
        };
    }

    private static String popularTitle(String listType) {
        return switch (listType) {
            case "TOP_TOPS" -> "인기 상의";
            case "TOP_BOTTOMS" -> "인기 하의";
            case "NEW_POPULAR" -> "신상품 인기 순위";
            case "TRENDING" -> "급상승 상품";
            default -> "인기 상품 TOP 10";
        };
    }
}
