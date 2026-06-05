package com.yammer.dto;

import com.yammer.entity.OrderPointEntity;
import java.util.UUID;

public record OrderPointResponse(
        UUID id,
        UUID locationId,
        String name,
        boolean payLater,
        boolean protocol,
        UUID menuId,
        UUID serviceOrderPointId) {

    public static OrderPointResponse from(OrderPointEntity entity) {
        return new OrderPointResponse(
                entity.getId(),
                entity.getLocationId(),
                entity.getName(),
                entity.isPayLater(),
                entity.isProtocol(),
                entity.getMenuId(),
                entity.getServiceOrderPointId());
    }
}
