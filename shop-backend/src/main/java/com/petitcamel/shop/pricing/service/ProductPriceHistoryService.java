package com.petitcamel.shop.pricing.service;

import com.petitcamel.shop.pricing.domain.ProductPriceHistory;
import com.petitcamel.shop.pricing.repository.ProductPriceHistoryRepository;
import com.petitcamel.shop.product.domain.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Service
public class ProductPriceHistoryService {

    private final ProductPriceHistoryRepository repository;
    private final Clock clock;

    public ProductPriceHistoryService(ProductPriceHistoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Appends a history row when the product is new or its normal / sale price changed. */
    @Transactional
    public void recordIfChanged(Product product, BigDecimal normalBefore, BigDecimal saleBefore, String reason,
                                Long actorId) {
        boolean isNew = normalBefore == null && saleBefore == null;
        if (!isNew && sameAmount(normalBefore, product.getNormalPrice())
                && sameAmount(saleBefore, product.getSalePrice())) {
            return;
        }
        ProductPriceHistory history = new ProductPriceHistory();
        history.setProductId(product.getProductId());
        history.setNormalPriceBefore(normalBefore);
        history.setNormalPriceAfter(product.getNormalPrice());
        history.setSalePriceBefore(saleBefore);
        history.setSalePriceAfter(product.getSalePrice());
        history.setReason(reason);
        history.setChangedBy(actorId);
        history.setChangedAt(clock.instant());
        repository.save(history);
    }

    @Transactional(readOnly = true)
    public List<ProductPriceHistory> list(Long productId) {
        return repository.findTop50ByProductIdOrderByChangedAtDesc(productId);
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }
}
