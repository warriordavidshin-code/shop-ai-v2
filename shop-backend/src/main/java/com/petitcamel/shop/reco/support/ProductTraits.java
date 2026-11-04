package com.petitcamel.shop.reco.support;

import java.time.Month;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based product traits (no AI): category group, item type, colour family, style and season are derived
 * from the category names, product name, keywords and SKU colours. Used whenever a product has no stored
 * AI/manual features, so recommendations work with AI switched off.
 */
public final class ProductTraits {

    public static final String TOP = "TOP";
    public static final String BOTTOM = "BOTTOM";
    public static final String DRESS = "DRESS";
    public static final String OUTER = "OUTER";
    public static final String ETC = "ETC";

    /** Item type keyword → group, most specific first ("셔츠 원피스" is a dress, "데님 스커트" a skirt). */
    private static final Map<String, String> ITEM_TYPES = new LinkedHashMap<>();
    private static final Map<String, String> ITEM_ALIASES = new LinkedHashMap<>();

    static {
        ITEM_TYPES.put("가디건", OUTER);
        ITEM_TYPES.put("자켓", OUTER);
        ITEM_TYPES.put("코트", OUTER);
        ITEM_TYPES.put("점퍼", OUTER);
        ITEM_TYPES.put("패딩", OUTER);
        ITEM_TYPES.put("블레이저", OUTER);
        ITEM_TYPES.put("트렌치", OUTER);
        ITEM_TYPES.put("원피스", DRESS);
        ITEM_TYPES.put("점프수트", DRESS);
        ITEM_TYPES.put("스커트", BOTTOM);
        ITEM_TYPES.put("슬랙스", BOTTOM);
        ITEM_TYPES.put("데님팬츠", BOTTOM);
        ITEM_TYPES.put("레깅스", BOTTOM);
        ITEM_TYPES.put("반바지", BOTTOM);
        ITEM_TYPES.put("팬츠", BOTTOM);
        ITEM_TYPES.put("블라우스", TOP);
        ITEM_TYPES.put("셔츠", TOP);
        ITEM_TYPES.put("니트", TOP);
        ITEM_TYPES.put("맨투맨", TOP);
        ITEM_TYPES.put("후드", TOP);
        ITEM_TYPES.put("티셔츠", TOP);
        ITEM_TYPES.put("나시", TOP);
        ITEM_TYPES.put("조끼", TOP);

        ITEM_ALIASES.put("재킷", "자켓");
        ITEM_ALIASES.put("드레스", "원피스");
        ITEM_ALIASES.put("치마", "스커트");
        ITEM_ALIASES.put("청바지", "데님팬츠");
        ITEM_ALIASES.put("청팬츠", "데님팬츠");
        ITEM_ALIASES.put("데님 팬츠", "데님팬츠");
        ITEM_ALIASES.put("진팬츠", "데님팬츠");
        ITEM_ALIASES.put("바지", "팬츠");
        ITEM_ALIASES.put("쇼츠", "반바지");
        ITEM_ALIASES.put("스웨터", "니트");
        ITEM_ALIASES.put("남방", "셔츠");
        ITEM_ALIASES.put("후디", "후드");
        ITEM_ALIASES.put("티", "티셔츠");
        ITEM_ALIASES.put("탑", "나시");
        ITEM_ALIASES.put("슬리브리스", "나시");
        ITEM_ALIASES.put("베스트", "조끼");
    }

    private static final Map<String, String> GROUP_WORDS = new LinkedHashMap<>();

    static {
        GROUP_WORDS.put("아우터", OUTER);
        GROUP_WORDS.put("원피스", DRESS);
        GROUP_WORDS.put("드레스", DRESS);
        GROUP_WORDS.put("하의", BOTTOM);
        GROUP_WORDS.put("팬츠", BOTTOM);
        GROUP_WORDS.put("스커트", BOTTOM);
        GROUP_WORDS.put("상의", TOP);
        GROUP_WORDS.put("탑", TOP);
    }

    private static final Map<String, String> COLOR_FAMILIES = new LinkedHashMap<>();

    static {
        put(COLOR_FAMILIES, "BLACK", "블랙", "검정", "black", "먹색");
        put(COLOR_FAMILIES, "WHITE", "화이트", "흰", "아이보리", "크림", "오프화이트", "white", "ivory");
        put(COLOR_FAMILIES, "GRAY", "그레이", "회색", "차콜", "멜란지", "gray", "grey", "charcoal");
        put(COLOR_FAMILIES, "BEIGE", "베이지", "카멜", "브라운", "모카", "오트밀", "샌드", "beige", "brown", "camel");
        put(COLOR_FAMILIES, "NAVY", "네이비", "곤색", "navy");
        put(COLOR_FAMILIES, "BLUE", "블루", "스카이", "소라", "연청", "중청", "진청", "흑청", "데님", "blue", "denim");
        put(COLOR_FAMILIES, "PINK", "핑크", "로즈", "pink");
        put(COLOR_FAMILIES, "RED", "레드", "와인", "버건디", "red", "wine");
        put(COLOR_FAMILIES, "GREEN", "그린", "카키", "민트", "올리브", "green", "khaki", "mint");
        put(COLOR_FAMILIES, "YELLOW", "옐로우", "머스타드", "레몬", "yellow", "mustard");
        put(COLOR_FAMILIES, "PURPLE", "퍼플", "라벤더", "바이올렛", "purple", "lavender");
        put(COLOR_FAMILIES, "ORANGE", "오렌지", "코랄", "orange", "coral");
    }

    public static final Set<String> NEUTRAL_COLORS = Set.of("BLACK", "WHITE", "GRAY", "BEIGE", "NAVY");

    private static final Map<String, String> STYLE_WORDS = new LinkedHashMap<>();

    static {
        put(STYLE_WORDS, "오피스", "오피스", "출근", "정장", "세미정장", "하객", "포멀");
        put(STYLE_WORDS, "캐주얼", "캐주얼", "데일리", "맨투맨", "후드", "데님", "티셔츠", "꾸안꾸");
        put(STYLE_WORDS, "페미닌", "페미닌", "러블리", "플라워", "레이스", "프릴", "셔링", "리본", "플리츠");
        put(STYLE_WORDS, "미니멀", "미니멀", "베이직", "심플", "무지", "기본");
        put(STYLE_WORDS, "빈티지", "빈티지", "레트로");
        put(STYLE_WORDS, "스포티", "스포티", "트레이닝", "조거", "애슬레저");
        put(STYLE_WORDS, "시크", "시크", "모던", "도시적");
    }

    private ProductTraits() {
    }

    private static void put(Map<String, String> map, String value, String... keys) {
        for (String key : keys) {
            map.put(key.toLowerCase(Locale.ROOT), value);
        }
    }

    /** Item type keyword (e.g. 니트, 슬랙스) from the product name, or null. */
    public static String itemType(String... texts) {
        List<String> order = List.copyOf(ITEM_TYPES.keySet());
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String compact = text.replace(" ", "");
            String best = null;
            for (String type : order) {
                if (compact.contains(type)) {
                    best = type;
                    break;
                }
            }
            for (Map.Entry<String, String> alias : ITEM_ALIASES.entrySet()) {
                String key = alias.getKey().replace(" ", "");
                if (key.length() >= 2 && compact.contains(key)
                        && (best == null || order.indexOf(alias.getValue()) < order.indexOf(best))) {
                    best = alias.getValue();
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    public static String groupOfItem(String itemType) {
        return itemType == null ? null : ITEM_TYPES.get(itemType);
    }

    /** Category group from item type first, then category / parent category names. */
    public static String group(String itemType, String categoryName, String parentCategoryName) {
        String byItem = groupOfItem(itemType);
        if (byItem != null) {
            return byItem;
        }
        for (String name : new String[]{categoryName, parentCategoryName}) {
            if (name == null) {
                continue;
            }
            for (Map.Entry<String, String> word : GROUP_WORDS.entrySet()) {
                if (name.contains(word.getKey())) {
                    return word.getValue();
                }
            }
            String fromName = groupOfItem(itemType(name));
            if (fromName != null) {
                return fromName;
            }
        }
        return ETC;
    }

    public static String colorFamily(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String value = color.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : COLOR_FAMILIES.entrySet()) {
            if (value.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static Set<String> colorFamilies(Iterable<String> colors) {
        Set<String> result = new LinkedHashSet<>();
        if (colors == null) {
            return result;
        }
        for (String color : colors) {
            String family = colorFamily(color);
            if (family != null) {
                result.add(family);
            }
        }
        return result;
    }

    public static Set<String> styles(String... texts) {
        Set<String> result = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String value = text.toLowerCase(Locale.ROOT);
            STYLE_WORDS.forEach((word, style) -> {
                if (value.contains(word)) {
                    result.add(style);
                }
            });
        }
        return result;
    }

    /** Seasons (봄/여름/가을/겨울) named in a free-text season field; 사계절 = all four. */
    public static Set<String> seasons(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String value = text.toUpperCase(Locale.ROOT);
        if (value.contains("사계절") || value.contains("ALL")) {
            return new LinkedHashSet<>(List.of("봄", "여름", "가을", "겨울"));
        }
        for (String season : List.of("봄", "여름", "가을", "겨울")) {
            if (value.contains(season)) {
                result.add(season);
            }
        }
        if (value.contains("SS")) {
            result.add("봄");
            result.add("여름");
        }
        if (value.contains("FW")) {
            result.add("가을");
            result.add("겨울");
        }
        if (value.contains("간절기")) {
            result.add("봄");
            result.add("가을");
        }
        return result;
    }

    public static String seasonOf(Month month) {
        return switch (month) {
            case MARCH, APRIL, MAY -> "봄";
            case JUNE, JULY, AUGUST -> "여름";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "가을";
            default -> "겨울";
        };
    }

    /** Tokens for name / keyword similarity: words of 2+ characters, lower-cased. */
    public static Set<String> tokens(String... texts) {
        Set<String> result = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            for (String token : text.toLowerCase(Locale.ROOT).split("[\\s,/·()\\[\\]#\\-_.]+")) {
                if (token.length() >= 2) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    public static Set<String> csv(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
