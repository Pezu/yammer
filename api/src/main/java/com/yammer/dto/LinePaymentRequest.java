package com.yammer.dto;

import com.yammer.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A payment operation against a table order (= an order point's running bill).
 * For {@link PaymentMode#FULL} {@code items} is ignored; for
 * {@link PaymentMode#PARTIAL} it lists the products + quantities to settle.
 */
public record LinePaymentRequest(
        @NotNull UUID orderPointId,
        @NotNull PaymentMode mode,
        @NotNull PaymentMethod method,
        @PositiveOrZero BigDecimal tip,
        @Valid List<LinePaymentItem> items) {

    public record LinePaymentItem(@NotNull UUID menuItemId, @Min(1) int quantity) {
    }
}
