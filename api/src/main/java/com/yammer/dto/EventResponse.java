package com.yammer.dto;

import com.yammer.entity.EventEntity;
import java.time.LocalDate;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID clientId,
        UUID locationId,
        String name,
        LocalDate startDate,
        LocalDate endDate) {

    public static EventResponse from(EventEntity entity) {
        return new EventResponse(
                entity.getId(),
                entity.getClientId(),
                entity.getLocationId(),
                entity.getName(),
                entity.getStartDate(),
                entity.getEndDate());
    }
}
