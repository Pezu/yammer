package com.yammer.dto;

import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderPointId,
        BigDecimal amount,
        BigDecimal tip,
        PaymentMethod method,
        String createdBy,
        LocalDateTime createdAt) {

    public static PaymentResponse from(PaymentEntity e) {
        return new PaymentResponse(
                e.getId(),
                e.getOrderPointId(),
                e.getAmount(),
                e.getTip(),
                e.getMethod(),
                e.getCreatedBy(),
                e.getCreatedAt());
    }
}
