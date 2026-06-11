package com.yammer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequest(
        UUID menuItemId,
        @NotBlank String name,
        @PositiveOrZero BigDecimal price,
        @Min(1) int quantity) {
}
