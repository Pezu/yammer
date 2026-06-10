package com.yammer.dto;

import java.math.BigDecimal;

/**
 * Sales totals for the report window. {@code totalSales} equals
 * {@code totalPaid + totalProtocol + remainingToPay + remainingProtocol}.
 */
public record SalesSummaryResponse(
        BigDecimal totalSales,
        BigDecimal totalPaid,
        BigDecimal totalProtocol,
        BigDecimal remainingToPay,
        BigDecimal remainingProtocol) {
}
