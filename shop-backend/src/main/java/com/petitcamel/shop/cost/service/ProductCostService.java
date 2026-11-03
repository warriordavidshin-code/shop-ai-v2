package com.petitcamel.shop.cost.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.cost.domain.ProductCostHistory;
import com.petitcamel.shop.cost.domain.SkuCost;
import com.petitcamel.shop.cost.dto.CostDtos;
import com.petitcamel.shop.cost.repository.ProductCostHistoryRepository;
import com.petitcamel.shop.cost.repository.SkuCostRepository;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.pricing.domain.PricingPolicy;
import com.petitcamel.shop.pricing.service.PricingPolicyService;
import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductSku;
import com.petitcamel.shop.product.repository.ProductRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase cost bookkeeping. Each confirmed receipt line appends an immutable {@link ProductCostHistory}
 * and updates the SKU's moving weighted average:
 * <pre>
 * basis    = min(stock before receipt, previously costed quantity)
 * new avg  = (old avg × basis + landed unit cost × received) / (basis + received)
 * </pre>
 * The cost applied to a sale follows the policy costing method (RECENT / WEIGHTED_AVERAGE / STANDARD),
 * falling back to the product's admin-entered purchase price when no receipt exists yet.
 */
@Service
public class ProductCostService {

    public record AppliedCost(BigDecimal unitCost, String source) {
    }

    private final ProductCostHistoryRepository historyRepository;
    private final SkuCostRepository skuCostRepository;
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final InventoryRepository inventoryRepository;
    private final PricingPolicyService pricingPolicyService;

    public ProductCostService(
            ProductCostHistoryRepository historyRepository,
            SkuCostRepository skuCostRepository,
            ProductRepository productRepository,
            ProductSkuRepository productSkuRepository,
            InventoryRepository inventoryRepository,
            PricingPolicyService pricingPolicyService) {
        this.historyRepository = historyRepository;
        this.skuCostRepository = skuCostRepository;
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.inventoryRepository = inventoryRepository;
        this.pricingPolicyService = pricingPolicyService;
    }

    /** Called inside the receipt-confirm transaction. */
    @Transactional
    public ProductCostHistory recordReceipt(
            Long productId, Long skuId, Long receiptItemId, Long ledgerId, Long supplierId, int quantity,
            BigDecimal unitCost, BigDecimal allocatedUnitCost, int stockBefore, Long actorId, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        BigDecimal allocated = allocatedUnitCost == null ? BigDecimal.ZERO : allocatedUnitCost;
        BigDecimal landed = unitCost.add(allocated).setScale(4, RoundingMode.HALF_UP);

        SkuCost skuCost = skuCostRepository.findById(skuId).orElse(null);
        BigDecimal avgBefore = skuCost == null ? null : skuCost.getAvgUnitCost();
        BigDecimal avgAfter;
        int costedAfter;
        if (skuCost == null) {
            skuCost = new SkuCost();
            skuCost.setSkuId(skuId);
            avgAfter = landed;
            costedAfter = quantity;
        } else {
            int basis = Math.max(0, Math.min(stockBefore, skuCost.getCostedQuantity()));
            BigDecimal totalValue = skuCost.getAvgUnitCost().multiply(BigDecimal.valueOf(basis))
                    .add(landed.multiply(BigDecimal.valueOf(quantity)));
            costedAfter = basis + quantity;
            avgAfter = totalValue.divide(BigDecimal.valueOf(costedAfter), 4, RoundingMode.HALF_UP);
        }
        skuCost.setAvgUnitCost(avgAfter);
        skuCost.setCostedQuantity(costedAfter);
        skuCost.setLastUnitCost(landed);
        skuCost.setLastReceivedAt(now);
        skuCost.setUpdatedAt(now);
        skuCostRepository.save(skuCost);

        ProductCostHistory history = new ProductCostHistory();
        history.setProductId(productId);
        history.setSkuId(skuId);
        history.setPurchaseReceiptItemId(receiptItemId);
        history.setPurchaseLedgerId(ledgerId);
        history.setSupplierId(supplierId);
        history.setQuantity(quantity);
        history.setUnitCost(unitCost.setScale(4, RoundingMode.HALF_UP));
        history.setAllocatedUnitCost(allocated.setScale(4, RoundingMode.HALF_UP));
        history.setLandedUnitCost(landed);
        history.setTotalCost(landed.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        history.setAvgCostBefore(avgBefore);
        history.setAvgCostAfter(avgAfter);
        history.setReceivedAt(now);
        history.setCreatedBy(actorId);
        history.setCreatedAt(now);
        return historyRepository.save(history);
    }

    /** Unit cost to snapshot on a sale, or null cost when nothing is known. */
    @Transactional(readOnly = true)
    public AppliedCost costForSale(Long productId, Long skuId) {
        return costForSale(productId, skuId, pricingPolicyService.current());
    }

    @Transactional(readOnly = true)
    public AppliedCost costForSale(Long productId, Long skuId, PricingPolicy policy) {
        String method = policy.getCostingMethod();
        Product product = productRepository.findById(productId).orElse(null);
        BigDecimal standard = product == null ? null : product.getPurchasePrice();
        SkuCost skuCost = skuId == null ? null : skuCostRepository.findById(skuId).orElse(null);
        switch (method) {
            case "STANDARD" -> {
                if (standard != null) {
                    return new AppliedCost(standard, "STANDARD");
                }
            }
            case "RECENT" -> {
                if (skuCost != null && skuCost.getLastUnitCost() != null) {
                    return new AppliedCost(skuCost.getLastUnitCost(), "RECENT");
                }
                var productRecent = historyRepository.findFirstByProductIdOrderByReceivedAtDescIdDesc(productId);
                if (productRecent.isPresent()) {
                    return new AppliedCost(productRecent.get().getLandedUnitCost(), "RECENT");
                }
            }
            default -> {
                if (skuCost != null) {
                    return new AppliedCost(skuCost.getAvgUnitCost(), "WEIGHTED_AVERAGE");
                }
            }
        }
        if (standard != null) {
            return new AppliedCost(standard, "STANDARD_FALLBACK");
        }
        if (skuCost != null) {
            return new AppliedCost(skuCost.getAvgUnitCost(), "WEIGHTED_AVERAGE_FALLBACK");
        }
        return new AppliedCost(null, "UNKNOWN");
    }

    @Transactional(readOnly = true)
    public List<CostDtos.CostHistoryResponse> history(Long productId) {
        requireProduct(productId);
        Map<Long, ProductSku> skus = productSkuRepository.findByProductId(productId).stream()
                .collect(Collectors.toMap(ProductSku::getSkuId, Function.identity()));
        return historyRepository.findByProductIdOrderByReceivedAtDescIdDesc(productId).stream()
                .map(h -> new CostDtos.CostHistoryResponse(
                        h.getId(), h.getSkuId(), optionName(skus.get(h.getSkuId())), h.getPurchaseReceiptItemId(),
                        h.getPurchaseLedgerId(), h.getSupplierId(), h.getQuantity(), h.getUnitCost(),
                        h.getAllocatedUnitCost(), h.getLandedUnitCost(), h.getTotalCost(), h.getAvgCostBefore(),
                        h.getAvgCostAfter(), h.getReceivedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CostDtos.CostSummaryResponse summary(Long productId) {
        Product product = requireProduct(productId);
        PricingPolicy policy = pricingPolicyService.current();
        List<ProductSku> skus = productSkuRepository.findByProductId(productId);
        List<Long> skuIds = skus.stream().map(ProductSku::getSkuId).toList();
        Map<Long, SkuCost> costs = skuIds.isEmpty() ? Map.of() : skuCostRepository.findBySkuIdIn(skuIds).stream()
                .collect(Collectors.toMap(SkuCost::getSkuId, Function.identity()));
        Map<Long, Inventory> inventories = skuIds.isEmpty() ? Map.of()
                : inventoryRepository.findBySkuIdIn(skuIds).stream()
                .collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));

        List<ProductCostHistory> history = historyRepository.findByProductIdOrderByReceivedAtDescIdDesc(productId);
        long totalQty = history.stream().mapToLong(ProductCostHistory::getQuantity).sum();
        BigDecimal totalCost = history.stream().map(ProductCostHistory::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal simpleAverage = totalQty == 0 ? null
                : totalCost.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
        BigDecimal recent = history.isEmpty() ? null : history.get(0).getLandedUnitCost();

        List<CostDtos.SkuCostLine> lines = new ArrayList<>();
        long stockTotal = 0;
        BigDecimal valueTotal = BigDecimal.ZERO;
        BigDecimal movingWeighted = BigDecimal.ZERO;
        long movingQty = 0;
        for (ProductSku sku : skus) {
            Inventory inventory = inventories.get(sku.getSkuId());
            int stock = inventory == null || inventory.getStockQuantity() == null ? 0 : inventory.getStockQuantity();
            SkuCost cost = costs.get(sku.getSkuId());
            AppliedCost applied = costForSale(productId, sku.getSkuId(), policy);
            BigDecimal value = applied.unitCost() == null ? null
                    : applied.unitCost().multiply(BigDecimal.valueOf(stock)).setScale(0, RoundingMode.HALF_UP);
            lines.add(new CostDtos.SkuCostLine(sku.getSkuId(), optionName(sku), stock,
                    cost == null ? null : cost.getAvgUnitCost(), cost == null ? null : cost.getLastUnitCost(),
                    applied.unitCost(), value));
            stockTotal += stock;
            if (value != null) {
                valueTotal = valueTotal.add(value);
            }
            if (cost != null) {
                int weight = Math.max(stock, 1);
                movingWeighted = movingWeighted.add(cost.getAvgUnitCost().multiply(BigDecimal.valueOf(weight)));
                movingQty += weight;
            }
        }
        BigDecimal movingAverage = movingQty == 0 ? null
                : movingWeighted.divide(BigDecimal.valueOf(movingQty), 2, RoundingMode.HALF_UP);
        AppliedCost productApplied = productLevelApplied(policy.getCostingMethod(), recent, movingAverage,
                product.getPurchasePrice());

        return new CostDtos.CostSummaryResponse(productId, policy.getCostingMethod(), productApplied.unitCost(),
                productApplied.source(), recent, movingAverage, simpleAverage, product.getPurchasePrice(), totalQty,
                totalCost, history.size(), stockTotal, valueTotal, lines);
    }

    private static AppliedCost productLevelApplied(String method, BigDecimal recent, BigDecimal moving,
                                                   BigDecimal standard) {
        BigDecimal chosen = switch (method) {
            case "STANDARD" -> standard;
            case "RECENT" -> recent;
            default -> moving;
        };
        if (chosen != null) {
            return new AppliedCost(chosen, method);
        }
        if (standard != null) {
            return new AppliedCost(standard, "STANDARD_FALLBACK");
        }
        return new AppliedCost(moving != null ? moving : recent, moving != null ? "WEIGHTED_AVERAGE_FALLBACK"
                : recent != null ? "RECENT_FALLBACK" : "UNKNOWN");
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    private static String optionName(ProductSku sku) {
        return sku == null ? null : sku.getColor() + "/" + sku.getSize();
    }
}
