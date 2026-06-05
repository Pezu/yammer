package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(@NotBlank String role) {
}
