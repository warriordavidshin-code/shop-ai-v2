package com.petitcamel.shop.admin.dto;

public record AdminDashboardResponse(
        long lowStockCount,
        long onSaleProductCount,
        long recentOrderCount,
        long pendingPaymentCount,
        long memberCount,
        long cancelRequestCount,
        long todayOrderCount,
        long shippingReadyCount,
        long shippingInTransitCount,
        long deliveredCount,
        long returnRequestCount
) {
}
