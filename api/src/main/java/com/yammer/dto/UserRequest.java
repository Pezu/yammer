package com.yammer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record UserRequest(
        @NotBlank String username,
        String name,
        // Required on create; blank/null on update means "keep current password".
        String password,
        String phone,
        @Email String email,
        List<String> roles,
        // Required for non-SUPER users; ignored (cleared) for SUPER users.
        UUID clientId) {
}
