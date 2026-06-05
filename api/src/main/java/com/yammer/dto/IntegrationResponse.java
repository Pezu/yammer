package com.yammer.dto;

import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.IntegrationType;
import java.util.UUID;

public record IntegrationResponse(UUID id, UUID locationId, String name, String ip, IntegrationType type) {

    public static IntegrationResponse from(IntegrationEntity entity) {
        return new IntegrationResponse(
                entity.getId(), entity.getLocationId(), entity.getName(), entity.getIp(), entity.getType());
    }
}
