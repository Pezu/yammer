package com.yammer.repository;

import com.yammer.entity.MenuEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<MenuEntity, UUID> {

    List<MenuEntity> findByLocationIdOrderByName(UUID locationId);

    List<MenuEntity> findByLocationIdAndEventIdOrderByName(UUID locationId, UUID eventId);

    /** Menus at a location, scoped to an event when one is given, else all of the location. */
    default List<MenuEntity> findByLocationAndOptionalEventOrderByName(UUID locationId, UUID eventId) {
        return eventId != null
                ? findByLocationIdAndEventIdOrderByName(locationId, eventId)
                : findByLocationIdOrderByName(locationId);
    }
}
