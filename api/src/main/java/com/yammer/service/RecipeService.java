package com.yammer.service;

import com.yammer.dto.MenuResponse;
import com.yammer.dto.RecipeComponentRequest;
import com.yammer.dto.RecipeComponentResponse;
import com.yammer.dto.RecipeItem;
import com.yammer.entity.MenuItemEntity;
import com.yammer.entity.RecipeComponentEntity;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.RecipeComponentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Recipes (Rețetar): the "combined" products across all menus of a location (+ event). */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final MenuService menuService;
    private final MenuItemRepository menuItemRepository;
    private final RecipeComponentRepository componentRepository;

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

    // --- recipe components (CRUD) for one combined product ---

    public List<RecipeComponentResponse> listComponents(UUID menuItemId) {
        assertAccessibleItem(menuItemId);
        return componentRepository.findByMenuItemIdOrderBySortOrderAscIdAsc(menuItemId).stream()
                .map(RecipeComponentResponse::from)
                .toList();
    }

    @Transactional
    public RecipeComponentResponse createComponent(UUID menuItemId, RecipeComponentRequest request) {
        assertAccessibleItem(menuItemId);
        RecipeComponentEntity e = new RecipeComponentEntity();
        e.setMenuItemId(menuItemId);
        e.setSortOrder((int) componentRepository.countByMenuItemId(menuItemId));
        apply(e, request);
        return RecipeComponentResponse.from(componentRepository.save(e));
    }

    @Transactional
    public RecipeComponentResponse updateComponent(UUID componentId, RecipeComponentRequest request) {
        RecipeComponentEntity e = componentRepository.findById(componentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Component not found"));
        assertAccessibleItem(e.getMenuItemId());
        apply(e, request);
        return RecipeComponentResponse.from(componentRepository.save(e));
    }

    @Transactional
    public void deleteComponent(UUID componentId) {
        RecipeComponentEntity e = componentRepository.findById(componentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Component not found"));
        assertAccessibleItem(e.getMenuItemId());
        componentRepository.delete(e);
    }

    private void apply(RecipeComponentEntity e, RecipeComponentRequest r) {
        e.setName(r.name() == null ? "" : r.name().trim());
        e.setQuantity(r.quantity());
        e.setUnit(r.unit() == null || r.unit().isBlank() ? null : r.unit().trim());
        e.setPercentage(r.percentage());
    }

    /** Verify the caller may access the combined product (via its menu's location). */
    private void assertAccessibleItem(UUID menuItemId) {
        MenuItemEntity item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        menuService.assertAccessibleMenu(item.getMenuId());
    }
}
