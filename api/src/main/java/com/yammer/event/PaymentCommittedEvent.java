package com.yammer.event;

import java.util.UUID;

/**
 * Published (inside the payment transaction) after a non-protocol payment is created.
 * Consumed AFTER_COMMIT to fiscalize the payment via the on-prem bridge.
 */
public record PaymentCommittedEvent(UUID paymentId) {
}
