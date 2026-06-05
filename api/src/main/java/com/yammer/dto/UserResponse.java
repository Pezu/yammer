package com.yammer.dto;

import com.yammer.entity.UserEntity;
import java.util.List;
import java.util.UUID;

/** User view without the password. */
public record UserResponse(
        UUID id, String username, String phone, String email, List<String> roles, UUID clientId) {

    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getUsername(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getRoles(),
                entity.getClientId());
    }
}
