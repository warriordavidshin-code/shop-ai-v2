package com.petitcamel.shop.purchase.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Name similarity for matching statement item names to catalog names (character bigram Dice + token overlap). */
public final class ProductNameSimilarity {

    private static final Set<String> STOP_WORDS = Set.of("여성", "여자", "우먼", "women", "womens", "lady", "레이디",
            "신상", "new", "베스트", "best");

    private ProductNameSimilarity() {
    }

    public static double score(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return 0;
        }
        if (na.equals(nb)) {
            return 1;
        }
        double dice = dice(na.replace(" ", ""), nb.replace(" ", ""));
        double tokens = tokenOverlap(na, nb);
        double containment = na.replace(" ", "").contains(nb.replace(" ", ""))
                || nb.replace(" ", "").contains(na.replace(" ", "")) ? 0.85 : 0;
        return Math.max(Math.max(dice, tokens), containment);
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]+", " ").trim();
        StringBuilder out = new StringBuilder();
        for (String token : lowered.split("\\s+")) {
            if (!token.isEmpty() && !STOP_WORDS.contains(token)) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(token);
            }
        }
        return out.toString();
    }

    private static double dice(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            return a.equals(b) ? 1 : 0;
        }
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        int overlap = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String gram = b.substring(i, i + 2);
            Integer count = grams.get(gram);
            if (count != null && count > 0) {
                overlap++;
                grams.put(gram, count - 1);
            }
        }
        return 2.0 * overlap / ((a.length() - 1) + (b.length() - 1));
    }

    private static double tokenOverlap(String a, String b) {
        Set<String> ta = new HashSet<>(java.util.Arrays.asList(a.split(" ")));
        Set<String> tb = new HashSet<>(java.util.Arrays.asList(b.split(" ")));
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        ta.retainAll(tb);
        return union.isEmpty() ? 0 : (double) ta.size() / union.size();
    }
}
