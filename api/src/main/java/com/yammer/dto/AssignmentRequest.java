package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AssignmentRequest(
        @NotNull UUID locationId,
        @NotBlank String parentName,
        List<UUID> userIds) {
}
