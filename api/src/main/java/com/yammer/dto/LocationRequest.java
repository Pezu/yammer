package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record LocationRequest(
        @NotBlank String name,
        // Required for SUPER (who choose the client); ignored for others (forced to own client).
        UUID clientId) {
}
