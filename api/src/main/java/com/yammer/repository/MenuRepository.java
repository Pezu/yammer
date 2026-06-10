package com.yammer.repository;

import com.yammer.entity.MenuEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<MenuEntity, UUID> {

    List<MenuEntity> findByLocationIdOrderByName(UUID locationId);

    List<MenuEntity> findByLocationIdAndEventIdOrderByName(UUID locationId, UUID eventId);
}
