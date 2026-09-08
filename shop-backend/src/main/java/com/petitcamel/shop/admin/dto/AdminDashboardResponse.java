package com.petitcamel.shop.admin.dto;

public record AdminDashboardResponse(
        long lowStockCount,
        long onSaleProductCount,
        long recentOrderCount,
        long pendingPaymentCount,
        long memberCount
) {
}
