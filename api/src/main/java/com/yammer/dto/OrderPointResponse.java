package com.yammer.dto;

import com.yammer.entity.OrderPointEntity;
import java.util.List;
import java.util.UUID;

public record OrderPointResponse(
        UUID id,
        UUID locationId,
        UUID eventId,
        String name,
        boolean payLater,
        boolean protocol,
        UUID menuId,
        UUID serviceOrderPointId,
        UUID printerId,
        UUID cashRegisterId,
        List<String> paymentMethods) {

    public static OrderPointResponse from(OrderPointEntity entity) {
        return new OrderPointResponse(
                entity.getId(),
                entity.getLocationId(),
                entity.getEventId(),
                entity.getName(),
                entity.isPayLater(),
                entity.isProtocol(),
                entity.getMenuId(),
                entity.getServiceOrderPointId(),
                entity.getPrinterId(),
                entity.getCashRegisterId(),
                entity.getPaymentMethods());
    }
}
