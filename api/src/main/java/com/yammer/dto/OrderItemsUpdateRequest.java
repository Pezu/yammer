package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/** New quantities for an order's (unpaid) items; quantity ≤ 0 deletes the item. */
public record OrderItemsUpdateRequest(List<ItemQuantity> items) {

    public record ItemQuantity(UUID id, int quantity) {
    }
}
