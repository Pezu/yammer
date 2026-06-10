package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record EventRequest(
        @NotNull UUID locationId,
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
