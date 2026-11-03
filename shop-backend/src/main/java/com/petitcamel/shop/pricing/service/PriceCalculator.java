package com.petitcamel.shop.pricing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure pricing math.
 * <pre>
 * gross margin rate = (price - cost) / price
 * target price      = cost / (1 - target margin rate)
 * </pre>
 * With margin basis VAT_EXCLUDED (general taxpayer, VAT-inclusive shop prices) the margin is measured on
 * price / (1 + vat) and the cost is expected without VAT; the displayed price then includes VAT again.
 * This is a pricing aid only; it does not compute VAT payable.
 */
public final class PriceCalculator {

    private PriceCalculator() {
    }

    public record Basis(boolean excludeVat, BigDecimal vatRate) {
        public static Basis of(String marginBasis, String taxType, BigDecimal vatRate) {
            boolean exclude = "VAT_EXCLUDED".equals(marginBasis) && "GENERAL".equals(taxType);
            return new Basis(exclude, vatRate == null ? BigDecimal.ZERO : vatRate);
        }

        BigDecimal toMarginBase(BigDecimal price) {
            return excludeVat ? price.divide(BigDecimal.ONE.add(vatRate), 6, RoundingMode.HALF_UP) : price;
        }

        BigDecimal fromMarginBase(BigDecimal base) {
            return excludeVat ? base.multiply(BigDecimal.ONE.add(vatRate)) : base;
        }
    }

    /** Unrounded price that yields {@code marginRate} on {@code cost}. */
    public static BigDecimal targetPrice(BigDecimal cost, BigDecimal marginRate, Basis basis) {
        if (marginRate.compareTo(BigDecimal.ONE) >= 0 || marginRate.signum() < 0) {
            throw new IllegalArgumentException("margin rate must be in [0, 1)");
        }
        BigDecimal base = cost.divide(BigDecimal.ONE.subtract(marginRate), 6, RoundingMode.HALF_UP);
        return basis.fromMarginBase(base);
    }

    /** Gross margin rate as a fraction (4 decimals), or null when price is zero. */
    public static BigDecimal marginRate(BigDecimal price, BigDecimal cost, Basis basis) {
        if (price == null || cost == null || price.signum() <= 0) {
            return null;
        }
        BigDecimal base = basis.toMarginBase(price);
        return base.subtract(cost).divide(base, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal grossProfit(BigDecimal price, BigDecimal cost, Basis basis) {
        return basis.toMarginBase(price).subtract(cost).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Applies the price-ending rule. Always rounds up so the margin never drops below the target.
     * END_900: next x,900 (37,200 → 37,900). END_900_ROUND_9000: same, but x8,900 becomes x9,000
     * (48,300 → 49,000).
     */
    public static long round(BigDecimal rawPrice, String mode) {
        long price = rawPrice.setScale(0, RoundingMode.CEILING).longValue();
        if (price <= 0) {
            return 0;
        }
        String safeMode = mode == null ? "NONE" : mode;
        return switch (safeMode) {
            case "UP_100" -> ceilTo(price, 100);
            case "UP_1000" -> ceilTo(price, 1000);
            case "END_900" -> end900(price);
            case "END_900_ROUND_9000" -> {
                long value = end900(price);
                yield (value / 1000) % 10 == 8 ? value + 100 : value;
            }
            default -> price;
        };
    }

    private static long end900(long price) {
        long thousands = Math.floorDiv(price - 900 + 999, 1000);
        return Math.max(thousands, 0) * 1000 + 900;
    }

    private static long ceilTo(long value, long unit) {
        return ((value + unit - 1) / unit) * unit;
    }
}
