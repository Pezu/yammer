package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/**
 * Public, customer-facing view of an order point reached by scanning its QR. The event is
 * resolved from the order point (the QR URL only carries the order point id), and {@code menu}
 * is the order point's default menu tree.
 */
public record CustomerOrderPointResponse(
        UUID id,
        String name,
        UUID eventId,
        String eventName,
        UUID clientId,
        List<MenuItemNode> menu) {
}
