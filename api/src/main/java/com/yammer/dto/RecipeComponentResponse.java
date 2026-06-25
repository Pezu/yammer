package com.yammer.dto;

import com.yammer.entity.RecipeComponentEntity;
import java.math.BigDecimal;
import java.util.UUID;

/** A recipe component row. */
public record RecipeComponentResponse(
        UUID id, String name, BigDecimal quantity, String unit, BigDecimal percentage) {

    public static RecipeComponentResponse from(RecipeComponentEntity e) {
        return new RecipeComponentResponse(
                e.getId(), e.getName(), e.getQuantity(), e.getUnit(), e.getPercentage());
    }
}
