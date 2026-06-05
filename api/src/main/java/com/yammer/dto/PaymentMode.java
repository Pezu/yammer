package com.yammer.dto;

public enum PaymentMode {
    /** Settle every unpaid line under the table order. */
    FULL,
    /** Settle the requested product quantities, splitting lines as needed. */
    PARTIAL
}
