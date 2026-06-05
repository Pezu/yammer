package com.yammer.repository;

import com.yammer.entity.MenuItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, UUID> {

    List<MenuItemEntity> findByMenuIdOrderBySortOrder(UUID menuId);
}
