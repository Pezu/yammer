package com.yammer.dto;

import java.util.UUID;

/** A "combined" product surfaced in the Recipes (Rețetar) list, with the menu it belongs to. */
public record RecipeItem(UUID id, String name, UUID menuId, String menuName) {}
