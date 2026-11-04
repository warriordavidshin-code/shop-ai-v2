package com.petitcamel.shop.reco.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecoScoringTest {

    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    private static final RecoScoring.Weights DEFAULT = new RecoScoring.Weights(.25, .15, .15, .20, .15, .10);

    private static ProductProfile product(long id, String name, long categoryId, String group, String item,
                                          Set<String> colors, double price, int ageDays) {
        return new ProductProfile(id, name, categoryId, "카테고리", group, item, colors, null, Set.of("캐주얼"),
                Set.of(), Set.of(), null, ProductTraits.tokens(name), Set.of(), price,
                NOW.minus(Duration.ofDays(ageDays)), "ON_SALE", 10, "RULE");
    }

    @Test
    void personalScoreFollowsTheWeightedFormula() {
        ProductProfile p = product(1, "블랙 니트", 3, "TOP", "니트", Set.of("BLACK"), 40000, 0);
        RecoScoring.Preference pref = new RecoScoring.Preference(Map.of(3L, 1.0), Map.of("TOP", 1.0),
                Map.of("BLACK", 1.0), Map.of(), 40000.0, 30000.0, 50000.0, Set.of(), 5);
        RecoScoring.Scored s = RecoScoring.personal(p, pref, 0.5, 0.4, DEFAULT, NOW, 1.0);
        double expected = 1.0 * .25 + 1.0 * .15 + 1.0 * .15 + 0.5 * .20 + 0.4 * .15 + 1.0 * .10;
        assertThat(s.score()).isCloseTo(expected, within(0.01));
        assertThat(s.breakdown()).containsKeys("categoryMatch", "colorMatch", "priceMatch", "purchaseSimilarity",
                "popularity", "freshness");
        assertThat(s.reason()).isNotBlank();
    }

    @Test
    void weightsChangeTheRanking() {
        ProductProfile sameCategory = product(1, "아이보리 니트", 3, "TOP", "니트", Set.of("WHITE"), 40000, 30);
        ProductProfile sameColor = product(2, "블랙 스커트", 5, "BOTTOM", "스커트", Set.of("BLACK"), 40000, 30);
        RecoScoring.Preference pref = new RecoScoring.Preference(Map.of(3L, 1.0), Map.of("TOP", 1.0),
                Map.of("BLACK", 1.0), Map.of(), 40000.0, 30000.0, 50000.0, Set.of(), 3);
        RecoScoring.Weights colorHeavy = new RecoScoring.Weights(.05, .80, .05, .05, .025, .025);
        assertThat(RecoScoring.personal(sameCategory, pref, 0, 0, DEFAULT, NOW, 1).score())
                .isGreaterThan(RecoScoring.personal(sameColor, pref, 0, 0, DEFAULT, NOW, 1).score());
        assertThat(RecoScoring.personal(sameColor, pref, 0, 0, colorHeavy, NOW, 1).score())
                .isGreaterThan(RecoScoring.personal(sameCategory, pref, 0, 0, colorHeavy, NOW, 1).score());
    }

    @Test
    void similarPrefersSameCategoryAndCoordinationNeedsRuleAndDifferentGroup() {
        ProductProfile knit = product(1, "블랙 니트", 3, "TOP", "니트", Set.of("BLACK"), 40000, 10);
        ProductProfile knit2 = product(2, "그레이 니트", 3, "TOP", "니트", Set.of("GRAY"), 42000, 10);
        ProductProfile slacks = product(3, "블랙 슬랙스", 5, "BOTTOM", "슬랙스", Set.of("BLACK"), 45000, 10);
        assertThat(RecoScoring.similar(knit, knit2).score()).isGreaterThan(RecoScoring.similar(knit, slacks).score());

        assertThat(RecoScoring.coordination(knit, knit2, 1.0, "가을")).isNull();
        assertThat(RecoScoring.coordination(knit, slacks, 0, "가을")).isNull();
        RecoScoring.Scored strong = RecoScoring.coordination(knit, slacks, 1.0, "가을");
        RecoScoring.Scored weak = RecoScoring.coordination(knit, slacks, 0.5, "가을");
        assertThat(strong).isNotNull();
        assertThat(strong.score()).isGreaterThan(weak.score());
    }

    @Test
    void traitsAreDerivedFromNamesAndCategories() {
        assertThat(ProductTraits.itemType("RIT 블랙 니트")).isEqualTo("니트");
        assertThat(ProductTraits.groupOfItem("슬랙스")).isEqualTo("BOTTOM");
        assertThat(ProductTraits.colorFamily("블랙")).isEqualTo("BLACK");
        assertThat(ProductTraits.seasonOf(Month.JULY)).isEqualTo("여름");
        assertThat(ProductTraits.seasons("가을 겨울 니트")).contains("가을", "겨울");
        assertThat(RecoScoring.priceSimilarity(40000, 40000)).isEqualTo(1.0);
        assertThat(RecoScoring.jaccard(List.of("a", "b"), List.of("b", "c"))).isCloseTo(1 / 3.0, within(0.01));
        assertThat(RecoScoring.freshness(NOW, NOW)).isEqualTo(1.0);
        assertThat(RecoScoring.freshness(NOW.minus(Duration.ofDays(90)), NOW)).isZero();
    }
}
