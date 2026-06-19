package com.yammer.dto;

import com.yammer.entity.FiscalStatus;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderPointId,
        UUID eventId,
        BigDecimal amount,
        BigDecimal tip,
        PaymentMethod method,
        FiscalStatus fiscalStatus,
        String receiptNumber,
        String createdBy,
        LocalDateTime createdAt) {

    public static PaymentResponse from(PaymentEntity e) {
        return new PaymentResponse(
                e.getId(),
                e.getOrderPointId(),
                e.getEventId(),
                e.getAmount(),
                e.getTip(),
                e.getMethod(),
                e.getFiscalStatus(),
                e.getReceiptNumber(),
                e.getCreatedBy(),
                e.getCreatedAt());
    }
}
