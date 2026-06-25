package com.yammer.dto;

import java.math.BigDecimal;

/** Create/update payload for a recipe component row. */
public record RecipeComponentRequest(String name, BigDecimal quantity, String unit, BigDecimal percentage) {}
