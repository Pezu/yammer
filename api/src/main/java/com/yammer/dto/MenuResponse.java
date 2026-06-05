package com.yammer.dto;

import com.yammer.entity.MenuEntity;
import java.util.UUID;

public record MenuResponse(UUID id, UUID locationId, String name) {

    public static MenuResponse from(MenuEntity entity) {
        return new MenuResponse(entity.getId(), entity.getLocationId(), entity.getName());
    }
}
