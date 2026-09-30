package com.petitcamel.shop.shipping.dto;

import java.util.List;

public record BulkInvoiceResponse(
        int total,
        int successCount,
        int failureCount,
        List<Row> results
) {

    public record Row(
            int row,
            String orderNumber,
            boolean success,
            String message
    ) {
    }
}
