package com.yammer.dto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String token,
        String username,
        List<String> roles,
        UUID clientId) {
}
