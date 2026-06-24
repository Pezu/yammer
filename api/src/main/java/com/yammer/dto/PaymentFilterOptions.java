package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/** Option lists for the payments-report filter combos (scoped to the event). */
public record PaymentFilterOptions(
        List<String> methods,
        List<OrderPointOption> orderPoints,
        List<String> users,
        List<String> fiscalStatuses) {

    public record OrderPointOption(UUID id, String name) {
    }
}
