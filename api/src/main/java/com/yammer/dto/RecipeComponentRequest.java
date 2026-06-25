package com.yammer.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Create/update payload for a recipe component row (references a non-combined product). */
public record RecipeComponentRequest(
        UUID componentItemId, BigDecimal quantity, String unit, BigDecimal percentage) {}
