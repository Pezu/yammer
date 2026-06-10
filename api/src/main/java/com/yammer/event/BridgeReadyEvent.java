package com.yammer.event;

/**
 * Published when the on-prem bridge (re)connects or sends a {@code HELLO}. Signals the
 * fiscal dispatcher to flush any PENDING receipts that accumulated while it was offline.
 */
public record BridgeReadyEvent() {
}
