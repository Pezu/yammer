package com.yammer.dto;

import java.math.BigDecimal;

/** Per order point totals for the report window. */
public record TableReportRow(
        String name,
        BigDecimal ordered,
        BigDecimal orderedPaid,
        BigDecimal orderedProtocol,
        BigDecimal paidCash,
        BigDecimal paidCard,
        BigDecimal protocol,
        BigDecimal remaining,
        BigDecimal remainingProtocol) {
}
