package com.yammer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One payment for the dashboard Payments report: when, where, type (method), amount, tip, fiscal. */
public record PaymentReportRow(
        UUID id,
        LocalDateTime createdAt,
        String orderPoint,
        String method,
        BigDecimal amount,
        BigDecimal tip,
        String fiscalStatus,
        String receiptNumber,
        String createdBy) {
}
