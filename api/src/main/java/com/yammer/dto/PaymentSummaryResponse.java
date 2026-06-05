package com.yammer.dto;

import com.yammer.entity.FiscalStatus;
import com.yammer.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One payment row for the waiter Payments page. */
public record PaymentSummaryResponse(
        UUID id,
        UUID orderPointId,
        String tableName,
        BigDecimal amount,
        PaymentMethod method,
        BigDecimal tip,
        FiscalStatus fiscalStatus,
        String receiptNumber,
        LocalDateTime createdAt) {
}
