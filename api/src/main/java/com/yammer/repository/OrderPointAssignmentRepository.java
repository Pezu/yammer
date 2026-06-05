package com.yammer.repository;

import com.yammer.entity.OrderPointAssignmentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPointAssignmentRepository
        extends JpaRepository<OrderPointAssignmentEntity, UUID> {

    List<OrderPointAssignmentEntity> findByLocationId(UUID locationId);

    List<OrderPointAssignmentEntity> findByLocationIdAndParentName(UUID locationId, String parentName);

    List<OrderPointAssignmentEntity> findByUserId(UUID userId);
}
