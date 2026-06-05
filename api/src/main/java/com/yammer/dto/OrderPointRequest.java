package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderPointRequest(
        @NotNull UUID locationId,
        @NotBlank String name,
        boolean payLater,
        boolean protocol,
        UUID menuId,
        UUID serviceOrderPointId) {
}
