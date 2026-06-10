package com.yammer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateOrderPointsBatchRequest(
        @NotNull UUID locationId,
        UUID eventId,
        @Min(1) int count,
        boolean payLater,
        UUID menuId,
        UUID serviceOrderPointId,
        UUID printerId,
        UUID cashRegisterId) {
}
