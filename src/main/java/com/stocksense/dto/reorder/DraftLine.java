package com.stocksense.dto.reorder;

import java.math.BigDecimal;

/** One line the reorder agent wants on a draft purchase order. */
public record DraftLine(Long productId, Integer quantity, BigDecimal unitPrice) {
}
