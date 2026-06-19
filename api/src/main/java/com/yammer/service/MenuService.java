package com.yammer.service;

import com.yammer.dto.MenuItemNode;
import com.yammer.dto.MenuRequest;
import com.yammer.dto.MenuResponse;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.MenuEntity;
import com.yammer.entity.MenuItemEntity;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.MenuRepository;
import com.yammer.security.AccessGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuItemRepository menuItemRepository;
    private final AccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<MenuResponse> listByLocation(UUID locationId, UUID eventId) {
        requireAccessibleLocation(locationId);
        List<MenuEntity> menus = eventId != null
                ? menuRepository.findByLocationIdAndEventIdOrderByName(locationId, eventId)
                : menuRepository.findByLocationIdOrderByName(locationId);
        return menus.stream().map(MenuResponse::from).toList();
    }

    public MenuResponse create(MenuRequest request) {
        requireAccessibleLocation(request.locationId());
        MenuEntity entity = new MenuEntity();
        entity.setLocationId(request.locationId());
        entity.setEventId(request.eventId());
        entity.setName(request.name().trim());
        return MenuResponse.from(menuRepository.save(entity));
    }

    public void delete(UUID menuId) {
        requireAccessibleMenu(menuId);
        menuRepository.deleteById(menuId); // cascades to menu_item rows
    }

    @Transactional(readOnly = true)
    public List<MenuItemNode> getTree(UUID menuId) {
        requireAccessibleMenu(menuId);
        return buildTree(menuItemRepository.findByMenuIdOrderBySortOrder(menuId));
    }

    /**
     * Menu tree without a tenant access check — for the public customer surface reached by scanning
     * an order point's QR (the menu isn't sensitive; access is implied by holding the link).
     */
    @Transactional(readOnly = true)
    public List<MenuItemNode> getTreeUnchecked(UUID menuId) {
        return buildTree(menuItemRepository.findByMenuIdOrderBySortOrder(menuId));
    }

    /**
     * Saves the tree by reconciling against the existing rows, preserving node ids:
     * nodes that carry a known id are updated in place, new nodes (null/unknown id) are
     * inserted, and rows no longer present are deleted. Keeping ids stable means edits
     * (e.g. a price change) don't disturb anything that references a menu_item id —
     * notably the waiter's drill-down path.
     */
    public List<MenuItemNode> saveTree(UUID menuId, List<MenuItemNode> nodes) {
        requireAccessibleMenu(menuId);

        Map<UUID, MenuItemEntity> existing = new HashMap<>();
        for (MenuItemEntity e : menuItemRepository.findByMenuIdOrderBySortOrder(menuId)) {
            existing.put(e.getId(), e);
        }

        Set<UUID> kept = new HashSet<>();
        reconcile(menuId, null, nodes, existing, kept);

        // rows that survived the reconcile but aren't in the incoming tree are gone;
        // a single batch delete is safe — parent_id is ON DELETE CASCADE and every
        // removed descendant is included in the set.
        List<UUID> toDelete = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!toDelete.isEmpty()) {
            menuItemRepository.deleteAllByIdInBatch(toDelete);
        }

        return buildTree(menuItemRepository.findByMenuIdOrderBySortOrder(menuId));
    }

    // --- helpers ---

    private void reconcile(
            UUID menuId,
            UUID parentId,
            List<MenuItemNode> nodes,
            Map<UUID, MenuItemEntity> existing,
            Set<UUID> kept) {
        if (nodes == null) {
            return;
        }
        int order = 0;
        for (MenuItemNode node : nodes) {
            MenuItemEntity entity = node.id() == null ? null : existing.get(node.id());
            if (entity == null) {
                entity = new MenuItemEntity();
                entity.setMenuId(menuId);
            }
            entity.setParentId(parentId);
            entity.setName(node.name() == null ? "" : node.name().trim());
            entity.setOrderable(node.orderable());
            entity.setPrice(node.orderable() ? node.price() : null);
            entity.setVatTypeId(node.orderable() ? node.vatTypeId() : null);
            entity.setSortOrder(order++);
            UUID id = menuItemRepository.save(entity).getId();
            kept.add(id);
            reconcile(menuId, id, node.children(), existing, kept);
        }
    }

    private List<MenuItemNode> buildTree(List<MenuItemEntity> all) {
        Map<UUID, List<MenuItemEntity>> byParent = new LinkedHashMap<>();
        for (MenuItemEntity e : all) {
            byParent.computeIfAbsent(e.getParentId(), k -> new ArrayList<>()).add(e);
        }
        return toNodes(byParent.get(null), byParent);
    }

    private List<MenuItemNode> toNodes(List<MenuItemEntity> items, Map<UUID, List<MenuItemEntity>> byParent) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(e -> new MenuItemNode(
                        e.getId(),
                        e.getName(),
                        e.isOrderable(),
                        e.getPrice(),
                        e.getVatTypeId(),
                        toNodes(byParent.get(e.getId()), byParent)))
                .toList();
    }

    private MenuEntity requireAccessibleMenu(UUID menuId) {
        MenuEntity menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found: " + menuId));
        accessGuard.requireAccessibleLocation(menu.getLocationId());
        return menu;
    }

    private LocationEntity requireAccessibleLocation(UUID locationId) {
        return accessGuard.requireAccessibleLocation(locationId);
    }
}
