package com.yammer.dto;

import com.yammer.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID orderPointId,
        @NotNull @Positive BigDecimal amount,
        BigDecimal tip,
        @NotNull PaymentMethod method) {
}
