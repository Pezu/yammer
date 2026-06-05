package com.yammer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull UUID orderPointId,
        @NotEmpty @Valid List<OrderItemRequest> items) {
}
