package com.yammer.repository;

import com.yammer.entity.OrderPointAssignmentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPointAssignmentRepository
        extends JpaRepository<OrderPointAssignmentEntity, UUID> {

    List<OrderPointAssignmentEntity> findByLocationId(UUID locationId);

    List<OrderPointAssignmentEntity> findByLocationIdAndEventId(UUID locationId, UUID eventId);

    List<OrderPointAssignmentEntity> findByLocationIdAndParentName(UUID locationId, String parentName);

    List<OrderPointAssignmentEntity> findByLocationIdAndEventIdAndParentName(
            UUID locationId, UUID eventId, String parentName);

    List<OrderPointAssignmentEntity> findByUserId(UUID userId);
}
