package com.yammer.entity;

/** Lifecycle of an online (Netopia) self-service payment intent. */
public enum OnlinePaymentStatus {
    /** Customer is on the gateway; no order exists yet. */
    PENDING,
    /** Gateway confirmed payment; the order + payment were created. */
    PAID,
    /** Gateway reported a failed/cancelled payment. */
    FAILED,
    /** Customer never completed payment; expired by the scheduled sweep. */
    EXPIRED
}
