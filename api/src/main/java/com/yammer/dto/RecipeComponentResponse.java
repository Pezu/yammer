package com.yammer.dto;

import com.yammer.entity.RecipeComponentEntity;
import java.math.BigDecimal;
import java.util.UUID;

/** A recipe component row: the referenced product (id + name snapshot) plus qty/unit/percentage. */
public record RecipeComponentResponse(
        UUID id, UUID componentItemId, String name,
        BigDecimal quantity, String unit, BigDecimal percentage) {

    public static RecipeComponentResponse from(RecipeComponentEntity e) {
        return new RecipeComponentResponse(
                e.getId(), e.getComponentItemId(), e.getName(),
                e.getQuantity(), e.getUnit(), e.getPercentage());
    }
}
