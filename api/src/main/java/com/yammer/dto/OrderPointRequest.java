package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record OrderPointRequest(
        @NotNull UUID locationId,
        UUID eventId,
        @NotBlank String name,
        boolean payLater,
        boolean protocol,
        UUID menuId,
        UUID serviceOrderPointId,
        UUID printerId,
        UUID cashRegisterId,
        List<String> paymentMethods) {
}
