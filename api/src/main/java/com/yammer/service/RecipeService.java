package com.yammer.service;

import com.yammer.dto.MenuResponse;
import com.yammer.dto.RecipeItem;
import com.yammer.entity.MenuItemEntity;
import com.yammer.repository.MenuItemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Recipes (Rețetar): the "combined" products across all menus of a location (+ event). */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final MenuService menuService;
    private final MenuItemRepository menuItemRepository;

    /** All combined products from every menu of the location (+ optional event). */
    public List<RecipeItem> combinedItems(UUID locationId, UUID eventId) {
        // listByLocation performs the tenant/location access check.
        List<MenuResponse> menus = menuService.listByLocation(locationId, eventId);
        if (menus.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> menuNames = new LinkedHashMap<>();
        for (MenuResponse m : menus) {
            menuNames.put(m.id(), m.name());
        }
        return menuItemRepository
                .findByMenuIdInAndOrderableTrueOrderByName(menuNames.keySet())
                .stream()
                .filter(MenuItemEntity::isCombined)
                .map(i -> new RecipeItem(i.getId(), i.getName(), i.getMenuId(), menuNames.get(i.getMenuId())))
                .toList();
    }
}
