package com.yammer.dto.netopia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Netopia's response to start-payment: a status and the {@code payment.paymentURL} to redirect to. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NetopiaStartResponse(Integer status, String message, PaymentData payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentData(String ntpID, String paymentURL, String token) {}
}
