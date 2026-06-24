package com.yammer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A customer's past order for the public QR order-history view: status, lines, and total. */
public record PublicOrderResponse(
        UUID id,
        long orderNo,
        String status,
        LocalDateTime createdAt,
        BigDecimal total,
        List<Item> items) {

    public record Item(String name, int quantity, BigDecimal price) {
    }
}
