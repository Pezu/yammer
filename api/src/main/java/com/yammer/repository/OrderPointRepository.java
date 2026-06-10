package com.yammer.repository;

import com.yammer.entity.OrderPointEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPointRepository extends JpaRepository<OrderPointEntity, UUID> {

    List<OrderPointEntity> findByLocationIdOrderByName(UUID locationId);

    List<OrderPointEntity> findByLocationIdAndEventIdOrderByName(UUID locationId, UUID eventId);

    List<OrderPointEntity> findByLocationIdIn(List<UUID> locationIds);

    /** Order points whose service point is one of the given order points. */
    List<OrderPointEntity> findByServiceOrderPointIdIn(Collection<UUID> serviceOrderPointIds);
}
