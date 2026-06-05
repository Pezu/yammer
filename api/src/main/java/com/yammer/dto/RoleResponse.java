package com.yammer.dto;

import com.yammer.entity.RoleEntity;
import java.util.UUID;

public record RoleResponse(UUID id, String role) {

    public static RoleResponse from(RoleEntity entity) {
        return new RoleResponse(entity.getId(), entity.getRole());
    }
}
