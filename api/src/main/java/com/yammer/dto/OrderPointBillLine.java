package com.yammer.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One product on an order point's bill: how much is paid and how much is still unpaid. */
public record OrderPointBillLine(
        UUID menuItemId,
        String name,
        BigDecimal price,
        long paidQty,
        long unpaidQty) {
}
