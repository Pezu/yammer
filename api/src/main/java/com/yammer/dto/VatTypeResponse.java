package com.yammer.dto;

import com.yammer.entity.VatTypeEntity;
import java.math.BigDecimal;
import java.util.UUID;

public record VatTypeResponse(UUID id, BigDecimal value) {

    public static VatTypeResponse from(VatTypeEntity entity) {
        return new VatTypeResponse(entity.getId(), entity.getValue());
    }
}
