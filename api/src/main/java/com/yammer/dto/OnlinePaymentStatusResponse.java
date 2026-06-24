package com.yammer.dto;

import java.util.UUID;

/** Status of an online payment intent, polled by the customer return page. */
public record OnlinePaymentStatusResponse(String status, UUID orderId) {}
