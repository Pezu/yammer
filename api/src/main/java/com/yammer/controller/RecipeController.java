package com.yammer.controller;

import com.yammer.dto.RecipeItem;
import com.yammer.service.RecipeService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recipes (Rețetar): combined products across a location's menus. */
@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')")
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public List<RecipeItem> combined(
            @RequestParam UUID locationId, @RequestParam(required = false) UUID eventId) {
        return recipeService.combinedItems(locationId, eventId);
    }
}
