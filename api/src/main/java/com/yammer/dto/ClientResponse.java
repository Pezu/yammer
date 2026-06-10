package com.yammer.dto;

import com.yammer.entity.ClientEntity;
import java.util.UUID;

public record ClientResponse(UUID id, String name, String phone, String email, boolean hasLogo) {

    public static ClientResponse from(ClientEntity entity) {
        return new ClientResponse(
                entity.getId(),
                entity.getName(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getLogoObject() != null);
    }
}
