package com.yammer.dto;

import java.math.BigDecimal;

/**
 * One ordered-product line for a dashboard drill-down: which waiter ordered it at which order point,
 * the product, its unit price, the quantity and the line total ({@code price * quantity}).
 */
public record TableItemReportRow(
        String waiter,
        String table,
        String name,
        BigDecimal price,
        long quantity,
        BigDecimal total) {
}
