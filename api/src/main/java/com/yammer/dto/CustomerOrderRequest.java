package com.yammer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * A customer-placed order from the public QR page. Only product ids + quantities are sent — the
 * server resolves name/price from the menu, so the client can't tamper with prices. For a pay-now
 * order point the client also sends {@code returnUrl} — where the gateway redirects the browser
 * after payment (the server appends the payment reference).
 */
public record CustomerOrderRequest(@NotEmpty List<Line> items, String returnUrl) {

    public record Line(@NotNull UUID menuItemId, @Min(1) int quantity) {
    }
}
