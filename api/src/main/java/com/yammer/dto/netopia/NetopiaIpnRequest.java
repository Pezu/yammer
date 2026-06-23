package com.yammer.dto.netopia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Netopia IPN (Instant Payment Notification) body. The gateway calls our notify URL server-to-server
 * whenever a payment's status changes. {@code order.orderID} is the reference we sent at start
 * (our {@code online_payment} id); {@code payment.status} is the gateway status code.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NetopiaIpnRequest(Order order, Payment payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(String ntpID, String orderID, Double amount, String currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(Integer status, String message, String maskedCardNumber, String token) {}
}
