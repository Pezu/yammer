package com.yammer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One 10-minute bucket of the sales report. */
public record SalesIntervalRow(
        LocalDateTime interval,
        BigDecimal amountOrdered,
        BigDecimal amountPaid,
        BigDecimal amountProtocol,
        long orderCount) {
}
