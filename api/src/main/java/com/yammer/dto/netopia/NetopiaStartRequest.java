package com.yammer.dto.netopia;

/**
 * Body of Netopia's {@code POST /payment/card/start} (v2 JSON API). Mirrors the gateway's expected
 * shape: a {@code config} block (URLs/locale) and an {@code order} block (amount + POS signature +
 * billing).
 */
public record NetopiaStartRequest(Config config, Order order) {

    public record Config(String emailTemplate, String notifyUrl, String redirectUrl, String language) {}

    public record Order(
            String orderID,
            Double amount,
            String posSignature,
            String dateTime,
            String currency,
            Billing billing) {}

    public record Billing(
            String email,
            String phone,
            String firstName,
            String lastName,
            String city,
            Integer country,
            String countryName,
            String state,
            String postalCode,
            String details) {}
}
