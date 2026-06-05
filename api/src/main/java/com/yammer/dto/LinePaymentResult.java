package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Summary of a line-level payment.
 *
 * @param paymentId   the created payment, or {@code null} when nothing was due (no-op)
 * @param amount      total of the line totals this payment covered
 * @param coveredLineIds the order-line ids now linked to this payment
 * @param splits      for every line that was split, the original id → resulting ids
 *                    (the reduced unpaid line and the new paid line)
 */
public record LinePaymentResult(
        UUID paymentId,
        BigDecimal amount,
        List<UUID> coveredLineIds,
        List<Split> splits) {

    public record Split(UUID oldId, List<UUID> resultingIds) {
    }
}
