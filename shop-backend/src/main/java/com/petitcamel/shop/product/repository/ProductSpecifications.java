package com.petitcamel.shop.product.repository;

import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.domain.ProductStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collection;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        return (root, query, cb) -> {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return null;
            }
            return root.get("categoryId").in(categoryIds);
        };
    }

    public static Specification<Product> keywordContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("productName")), pattern),
                    cb.like(cb.lower(root.get("brandName")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), pattern));
        };
    }

    public static Specification<Product> salePriceGte(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("salePrice"), minPrice);
    }

    public static Specification<Product> salePriceLte(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("salePrice"), maxPrice);
    }

    public static Specification<Product> hasColor(String color) {
        return (root, query, cb) -> {
            if (color == null || color.isBlank()) {
                return null;
            }
            query.distinct(true);
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<ProductSku> sku = subquery.from(ProductSku.class);
            subquery.select(sku.get("productId"))
                    .where(
                            cb.equal(sku.get("productId"), root.get("productId")),
                            cb.equal(cb.lower(sku.get("color")), color.trim().toLowerCase()));
            return cb.exists(subquery);
        };
    }

    public static Specification<Product> hasSize(String size) {
        return (root, query, cb) -> {
            if (size == null || size.isBlank()) {
                return null;
            }
            query.distinct(true);
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<ProductSku> sku = subquery.from(ProductSku.class);
            subquery.select(sku.get("productId"))
                    .where(
                            cb.equal(sku.get("productId"), root.get("productId")),
                            cb.equal(cb.lower(sku.get("size")), size.trim().toLowerCase()));
            return cb.exists(subquery);
        };
    }

    public static Specification<Product> hasAvailableStock() {
        return (root, query, cb) -> {
            query.distinct(true);
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<ProductSku> sku = subquery.from(ProductSku.class);
            Root<Inventory> inventory = subquery.from(Inventory.class);
            subquery.select(sku.get("productId"))
                    .where(
                            cb.equal(sku.get("productId"), root.get("productId")),
                            cb.equal(inventory.get("skuId"), sku.get("skuId")),
                            cb.greaterThan(
                                    cb.diff(inventory.get("stockQuantity"), inventory.get("reservedQuantity")),
                                    0));
            return cb.exists(subquery);
        };
    }
}
