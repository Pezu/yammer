package com.yammer.controller;

import com.yammer.dto.RecipeComponentRequest;
import com.yammer.dto.RecipeComponentResponse;
import com.yammer.dto.RecipeItem;
import com.yammer.service.RecipeService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** Non-combined products a recipe component can reference (autocomplete options). */
    @GetMapping("/product-options")
    public List<RecipeItem> productOptions(
            @RequestParam UUID locationId, @RequestParam(required = false) UUID eventId) {
        return recipeService.componentOptions(locationId, eventId);
    }

    // --- recipe components for one combined product ---

    @GetMapping("/components")
    public List<RecipeComponentResponse> components(@RequestParam UUID menuItemId) {
        return recipeService.listComponents(menuItemId);
    }

    @PostMapping("/components")
    public RecipeComponentResponse createComponent(
            @RequestParam UUID menuItemId, @RequestBody RecipeComponentRequest request) {
        return recipeService.createComponent(menuItemId, request);
    }

    @PutMapping("/components/{id}")
    public RecipeComponentResponse updateComponent(
            @PathVariable UUID id, @RequestBody RecipeComponentRequest request) {
        return recipeService.updateComponent(id, request);
    }

    @DeleteMapping("/components/{id}")
    public void deleteComponent(@PathVariable UUID id) {
        recipeService.deleteComponent(id);
    }
}
