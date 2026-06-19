package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/** Option lists for the orders-report filters (order point + waiter combos), scoped to the caller. */
public record OrderFilterOptions(List<OrderPointOption> orderPoints, List<String> waiters) {

    public record OrderPointOption(UUID id, String name) {
    }
}
