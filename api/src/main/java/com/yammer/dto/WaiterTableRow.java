package com.yammer.dto;

import java.math.BigDecimal;

/** Per waiter + order point totals for the report window. */
public record WaiterTableRow(
        String waiter,
        String table,
        BigDecimal ordered,
        BigDecimal paid,
        BigDecimal tip,
        BigDecimal protocol) {
}
