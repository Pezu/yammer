package com.yammer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank String name,
        String phone,
        @Email String email) {
}
