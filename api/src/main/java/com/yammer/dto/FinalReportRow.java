package com.yammer.dto;

import java.math.BigDecimal;

/**
 * Final report: per user (the payment's {@code created_by}) and order point — money the user took
 * in, split by method (card/cash) and tip. Protocol is excluded, so the totals reconcile with the
 * dashboard's "paid" figures.
 */
public record FinalReportRow(
        String userName,
        String table,
        BigDecimal paidCard,
        BigDecimal paidCash,
        BigDecimal tipCard,
        BigDecimal tipCash) {
}
