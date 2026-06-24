package com.yammer.dto;

import java.util.UUID;

/**
 * Result of placing a self-service order. For a pay-later order point the order is created
 * immediately ({@code orderId} set, {@code paymentUrl} null). For a pay-now order point no order
 * exists yet — the customer is redirected to {@code paymentUrl} and the order is created once the
 * gateway confirms; {@code reference} is the online-payment id the return page polls for status.
 */
public record CustomerOrderResponse(UUID orderId, String paymentUrl, UUID reference, UUID customerId) {

    public static CustomerOrderResponse placed(UUID orderId) {
        return new CustomerOrderResponse(orderId, null, null, null);
    }

    public static CustomerOrderResponse redirect(UUID reference, String paymentUrl, UUID customerId) {
        return new CustomerOrderResponse(null, paymentUrl, reference, customerId);
    }
}
