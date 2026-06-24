package com.stocksense.dto.reorder;

import com.stocksense.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A purchase order enriched with supplier/product names and line totals, for the UI. */
public record PurchaseOrderView(
        Long id,
        Long branchId,
        Long supplierId,
        String supplierName,
        OrderStatus status,
        boolean createdByAgent,
        Long approvedBy,
        Instant createdAt,
        BigDecimal totalAmount,
        List<LineView> lines) {

    public record LineView(
            Long productId,
            String sku,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }
}
