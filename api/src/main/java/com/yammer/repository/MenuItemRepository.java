package com.yammer.repository;

import com.yammer.entity.MenuItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, UUID> {

    List<MenuItemEntity> findByMenuIdOrderBySortOrder(UUID menuId);

    /** Orderable products (not categories) across the given menus, name-ordered — for product search. */
    List<MenuItemEntity> findByMenuIdInAndOrderableTrueOrderByName(Collection<UUID> menuIds);
}
