package com.yammer.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Per-table takings for the waiter Statistics page. */
public record TableStatsResponse(
        UUID orderPointId,
        String name,
        BigDecimal paidCard,
        BigDecimal paidCash,
        BigDecimal tipCard,
        BigDecimal tipCash,
        BigDecimal unpaid,
        BigDecimal settled,
        boolean protocol) {
}
