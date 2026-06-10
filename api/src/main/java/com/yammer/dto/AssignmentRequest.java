package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AssignmentRequest(
        @NotNull UUID locationId,
        UUID eventId,
        @NotBlank String parentName,
        List<UUID> userIds) {
}
