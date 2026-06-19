package com.yammer.dto;

import com.yammer.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Place an order at an order point.
 *
 * <p>{@code paymentMethod} drives the non-pay-later (immediate POS) flow: when present the order is
 * created already DELIVERED and settled in full with that method (plus {@code tip}). When null the
 * order is created ORDERED for the pay-later kanban flow (settled later from the table).
 */
public record PlaceOrderRequest(
        @NotNull UUID orderPointId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        PaymentMethod paymentMethod,
        @PositiveOrZero BigDecimal tip) {
}
