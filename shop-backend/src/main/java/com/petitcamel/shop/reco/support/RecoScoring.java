package com.petitcamel.shop.reco.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java scoring used by every recommendation area. Each component is normalised to 0..1 and the result
 * carries its breakdown so the admin screens can show why a product was recommended. No AI call happens here.
 */
public final class RecoScoring {

    private RecoScoring() {
    }

    public record Scored(Long productId, double score, Map<String, Double> breakdown, String reason) {
    }

    /** Weights of the personal score (admin-editable). */
    public record Weights(double category, double color, double price, double purchase, double popularity,
                          double freshness) {
        public double sum() {
            return category + color + price + purchase + popularity + freshness;
        }
    }

    /** Taste profile of a viewer: each map is normalised so its maximum is 1. */
    public record Preference(Map<Long, Double> categories, Map<String, Double> groups, Map<String, Double> colors,
                             Map<String, Double> styles, Double priceAvg, Double priceMin, Double priceMax,
                             Set<String> keywords, int signalCount) {
        public boolean empty() {
            return signalCount == 0;
        }
    }

    // ------------------------------------------------------------------ personal score

    /**
     * categoryMatch*w1 + colorMatch*w2 + priceMatch*w3 + purchaseSimilarity*w4 + popularity*w5 + freshness*w6,
     * divided by the weight sum so the score stays within 0..1.
     */
    public static Scored personal(ProductProfile p, Preference pref, double purchaseSimilarity, double popularity,
                                  Weights w, Instant now, double boost) {
        double category = categoryMatch(p, pref);
        double color = colorMatch(p, pref);
        double price = priceMatch(p.price(), pref);
        double fresh = freshness(p.createdAt(), now);
        double sum = w.sum() <= 0 ? 1 : w.sum();
        double score = (category * w.category() + color * w.color() + price * w.price()
                + purchaseSimilarity * w.purchase() + popularity * w.popularity() + fresh * w.freshness()) / sum;
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("categoryMatch", round(category));
        breakdown.put("colorMatch", round(color));
        breakdown.put("priceMatch", round(price));
        breakdown.put("purchaseSimilarity", round(purchaseSimilarity));
        breakdown.put("popularity", round(popularity));
        breakdown.put("freshness", round(fresh));
        if (boost != 1.0) {
            breakdown.put("ruleBoost", round(boost));
        }
        String reason;
        if (purchaseSimilarity >= 0.5) {
            reason = "구매·관심 상품과 비슷해요";
        } else if (category >= 0.6) {
            reason = "자주 보신 카테고리예요";
        } else if (color >= 0.6) {
            reason = "선호하시는 색상이에요";
        } else if (fresh >= 0.7) {
            reason = "새로 들어온 상품이에요";
        } else {
            reason = "많은 분들이 찾는 상품이에요";
        }
        return new Scored(p.productId(), round(score * boost), breakdown, reason);
    }

    static double categoryMatch(ProductProfile p, Preference pref) {
        double byCategory = pref.categories().getOrDefault(p.categoryId(), 0.0);
        double byGroup = pref.groups().getOrDefault(p.group(), 0.0) * 0.8;
        double byKeyword = 0;
        if (!pref.keywords().isEmpty()) {
            for (String keyword : pref.keywords()) {
                if (p.nameTokens().stream().anyMatch(t -> t.contains(keyword) || keyword.contains(t))
                        || p.keywordTokens().contains(keyword)) {
                    byKeyword = 0.9;
                    break;
                }
            }
        }
        return clamp(Math.max(byCategory, Math.max(byGroup, byKeyword)));
    }

    static double colorMatch(ProductProfile p, Preference pref) {
        double best = 0;
        for (String color : p.colorFamilies()) {
            best = Math.max(best, pref.colors().getOrDefault(color, 0.0));
        }
        return clamp(best);
    }

    static double priceMatch(double price, Preference pref) {
        if (pref.priceAvg() == null || pref.priceAvg() <= 0) {
            return 0.5;
        }
        double avg = pref.priceAvg();
        double tolerance = Math.max(avg * 0.5, 1);
        return clamp(1 - Math.abs(price - avg) / tolerance);
    }

    public static double freshness(Instant createdAt, Instant now) {
        if (createdAt == null) {
            return 0;
        }
        double days = Math.max(0, Duration.between(createdAt, now).toHours() / 24.0);
        return clamp(1 - days / 60.0);
    }

    // ------------------------------------------------------------------ similar products

    /**
     * Category .30, name .15, colour .15, price .15, style .10, material .05, keywords/description .10.
     * Image embeddings can be added later as one more component without changing callers.
     */
    public static Scored similar(ProductProfile base, ProductProfile other) {
        double category = base.categoryId().equals(other.categoryId()) ? 1
                : base.group().equals(other.group()) && !ProductTraits.ETC.equals(base.group()) ? 0.6 : 0;
        if (base.itemType() != null && base.itemType().equals(other.itemType())) {
            category = Math.max(category, 0.9);
        }
        double name = jaccard(base.nameTokens(), other.nameTokens());
        double color = jaccard(base.colorFamilies(), other.colorFamilies());
        double price = priceSimilarity(base.price(), other.price());
        double style = jaccard(base.styles(), other.styles());
        double material = base.material() != null && other.material() != null
                && sameMaterial(base.material(), other.material()) ? 1 : 0;
        double keywords = jaccard(base.keywordTokens(), other.keywordTokens());
        double score = category * 0.30 + name * 0.15 + color * 0.15 + price * 0.15 + style * 0.10
                + material * 0.05 + keywords * 0.10;
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("category", round(category));
        breakdown.put("name", round(name));
        breakdown.put("color", round(color));
        breakdown.put("price", round(price));
        breakdown.put("style", round(style));
        breakdown.put("material", round(material));
        breakdown.put("keywords", round(keywords));
        String reason = color >= 0.5 && category >= 0.6 ? "같은 종류·비슷한 색상이에요"
                : price >= 0.8 && category >= 0.6 ? "비슷한 가격대의 같은 종류예요"
                : "이 상품과 비슷한 스타일이에요";
        return new Scored(other.productId(), round(score), breakdown, reason);
    }

    // ------------------------------------------------------------------ coordination

    /**
     * Category combination (admin rules) .35, colour harmony .20, style .15, season .10, price balance .10,
     * stock .10. Returns null when no rule allows combining the two categories.
     */
    public static Scored coordination(ProductProfile base, ProductProfile other, double ruleWeight, String currentSeason) {
        if (ruleWeight <= 0 || base.group().equals(other.group())) {
            return null;
        }
        double category = clamp(ruleWeight);
        double color = colorHarmony(base.colorFamilies(), other.colorFamilies());
        double style = base.styles().isEmpty() || other.styles().isEmpty() ? 0.5
                : Math.max(jaccard(base.styles(), other.styles()), 0.2);
        double season;
        if (base.seasons().isEmpty() || other.seasons().isEmpty()) {
            season = other.seasons().contains(currentSeason) ? 0.8 : 0.6;
        } else {
            Set<String> overlap = new HashSet<>(base.seasons());
            overlap.retainAll(other.seasons());
            season = overlap.isEmpty() ? 0.2 : 1;
        }
        double priceBalance = priceSimilarity(base.price(), other.price());
        double stock = clamp(other.availableStock() / 10.0);
        double score = category * 0.35 + color * 0.20 + style * 0.15 + season * 0.10 + priceBalance * 0.10
                + stock * 0.10;
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("categoryRule", round(category));
        breakdown.put("colorHarmony", round(color));
        breakdown.put("style", round(style));
        breakdown.put("season", round(season));
        breakdown.put("priceBalance", round(priceBalance));
        breakdown.put("stock", round(stock));
        String label = other.itemType() != null ? other.itemType() : groupLabel(other.group());
        return new Scored(other.productId(), round(score), breakdown, label + "와(과) 함께 코디하기 좋아요");
    }

    /** Neutral colours go with anything; the same family reads as tone-on-tone; other pairs score lower. */
    public static double colorHarmony(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.6;
        }
        double best = 0;
        for (String x : a) {
            for (String y : b) {
                double value;
                if (ProductTraits.NEUTRAL_COLORS.contains(x) || ProductTraits.NEUTRAL_COLORS.contains(y)) {
                    value = 0.9;
                } else if (x.equals(y)) {
                    value = 0.7;
                } else if ("BLUE".equals(x) || "BLUE".equals(y)) {
                    value = 0.75;
                } else {
                    value = 0.4;
                }
                best = Math.max(best, value);
            }
        }
        return best;
    }

    public static String groupLabel(String group) {
        return switch (group == null ? "" : group) {
            case ProductTraits.TOP -> "상의";
            case ProductTraits.BOTTOM -> "하의";
            case ProductTraits.DRESS -> "원피스";
            case ProductTraits.OUTER -> "아우터";
            default -> "상품";
        };
    }

    // ------------------------------------------------------------------ helpers

    public static double priceSimilarity(double a, double b) {
        double max = Math.max(a, b);
        if (max <= 0) {
            return 1;
        }
        return clamp(1 - Math.abs(a - b) / max);
    }

    public static double jaccard(Collection<String> a, Collection<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return (double) inter.size() / union.size();
    }

    private static boolean sameMaterial(String a, String b) {
        String x = a.replace(" ", "");
        String y = b.replace(" ", "");
        for (String token : new String[]{"면", "코튼", "울", "린넨", "폴리", "레이온", "아크릴", "니트", "데님", "실크", "가죽", "스판"}) {
            if (x.contains(token) && y.contains(token)) {
                return true;
            }
        }
        return x.equalsIgnoreCase(y);
    }

    public static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
