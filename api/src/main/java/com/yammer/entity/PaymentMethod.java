package com.yammer.entity;

public enum PaymentMethod {
    CASH,
    CARD,
    PROTOCOL,
    /** Online card payment settled through the Netopia gateway (customer self-service QR flow). */
    ONLINE
}
