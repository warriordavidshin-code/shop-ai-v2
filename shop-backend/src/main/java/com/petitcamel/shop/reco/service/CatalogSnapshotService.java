package com.petitcamel.shop.reco.service;

import com.petitcamel.shop.reco.support.ProductProfile;
import com.petitcamel.shop.reco.support.ProductTraits;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory snapshot of every product's traits and sellable stock, rebuilt at most once a minute. Stored
 * AI/manual features ({@code product_ai_features}) override the rule-derived traits field by field.
 */
@Service
public class CatalogSnapshotService {

    private static final long TTL_MS = 60_000;

    private final JdbcTemplate jdbc;
    private volatile Snapshot cached;

    public CatalogSnapshotService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Snapshot(Map<Long, ProductProfile> products, long builtAtMillis) {
        public ProductProfile get(Long productId) {
            return products.get(productId);
        }
    }

    public Snapshot snapshot() {
        Snapshot current = cached;
        if (current != null && System.currentTimeMillis() - current.builtAtMillis() < TTL_MS) {
            return current;
        }
        current = build();
        cached = current;
        return current;
    }

    public void invalidate() {
        cached = null;
    }

    private Snapshot build() {
        Map<Long, ProductProfile> products = new LinkedHashMap<>();
        jdbc.query("""
                select p.product_id, p.product_name, p.category_id, c.name, pc.name, p.sale_price, p.status,
                       p.season, p.material, p.search_keywords, p.summary, p.created_at,
                       coalesce(st.available, 0), sk.colors,
                       f.category_group, f.item_type, f.colors, f.color_tone, f.style_tags, f.design_tags, f.season,
                       f.material, f.keywords, f.source
                from product p
                join category c on c.category_id = p.category_id
                left join category pc on pc.category_id = c.parent_id
                left join (select s.product_id,
                                  sum(greatest(coalesce(i.stock_quantity, 0) - coalesce(i.reserved_quantity, 0), 0)) as available
                           from product_sku s left join inventory i on i.sku_id = s.sku_id
                           where s.status = 'ON_SALE' group by s.product_id) st on st.product_id = p.product_id
                left join (select product_id, string_agg(distinct color, ',') as colors
                           from product_sku where status <> 'DISCONTINUED' group by product_id) sk
                       on sk.product_id = p.product_id
                left join product_ai_features f on f.product_id = p.product_id
                """, rs -> {
            ProductProfile profile = profile(rs);
            products.put(profile.productId(), profile);
        });
        return new Snapshot(Collections.unmodifiableMap(products), System.currentTimeMillis());
    }

    private static ProductProfile profile(ResultSet rs) throws SQLException {
        long productId = rs.getLong(1);
        String name = rs.getString(2);
        long categoryId = rs.getLong(3);
        String categoryName = rs.getString(4);
        String parentName = rs.getString(5);
        double price = rs.getBigDecimal(6).doubleValue();
        String status = rs.getString(7);
        String season = rs.getString(8);
        String material = rs.getString(9);
        String keywords = rs.getString(10);
        String summary = rs.getString(11);
        Instant createdAt = rs.getTimestamp(12).toInstant();
        int available = rs.getInt(13);
        Set<String> skuColors = ProductTraits.csv(rs.getString(14));

        String featureSource = rs.getString(24);
        boolean stored = featureSource != null && !"RULE".equals(featureSource);
        String itemType = stored && rs.getString(16) != null ? rs.getString(16)
                : ProductTraits.itemType(name, categoryName, parentName);
        String group = stored && rs.getString(15) != null && !"ETC".equals(rs.getString(15)) ? rs.getString(15)
                : ProductTraits.group(itemType, categoryName, parentName);
        Set<String> colors = ProductTraits.colorFamilies(skuColors);
        if (stored && rs.getString(17) != null) {
            colors.addAll(ProductTraits.colorFamilies(ProductTraits.csv(rs.getString(17))));
        }
        Set<String> styles = ProductTraits.styles(name, keywords, summary);
        if (stored && rs.getString(19) != null) {
            styles.addAll(ProductTraits.csv(rs.getString(19)));
        }
        Set<String> designs = stored ? ProductTraits.csv(rs.getString(20)) : new LinkedHashSet<>();
        Set<String> seasons = ProductTraits.seasons(season);
        if (stored && rs.getString(21) != null) {
            seasons.addAll(ProductTraits.seasons(rs.getString(21)));
        }
        String mat = material != null ? material : stored ? rs.getString(22) : null;
        Set<String> keywordTokens = ProductTraits.tokens(keywords, summary);
        if (stored && rs.getString(23) != null) {
            keywordTokens.addAll(ProductTraits.tokens(rs.getString(23)));
        }
        keywordTokens.addAll(designs);
        return new ProductProfile(productId, name, categoryId, categoryName, group, itemType, colors,
                stored ? rs.getString(18) : null, styles, designs, seasons, mat, ProductTraits.tokens(name),
                keywordTokens, price, createdAt, status, available, featureSource == null ? "RULE" : featureSource);
    }

    /** ON_SALE products with at least {@code minStock} sellable units that are not excluded by a rule. */
    public static boolean eligible(ProductProfile p, int minStock, Set<Long> excluded) {
        return p != null && p.onSale() && p.availableStock() >= Math.max(1, minStock)
                && !excluded.contains(p.productId());
    }

    public static List<ProductProfile> eligibleList(Snapshot snapshot, int minStock, Set<Long> excluded) {
        return snapshot.products().values().stream().filter(p -> eligible(p, minStock, excluded)).toList();
    }
}
