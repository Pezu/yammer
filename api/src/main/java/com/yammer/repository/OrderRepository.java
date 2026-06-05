package com.yammer.repository;

import com.yammer.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByOrderPointIdInOrderByCreatedAtDesc(List<UUID> orderPointIds);

    List<OrderEntity> findByOrderPointIdOrderByCreatedAtAsc(UUID orderPointId);

    /** Orders for the given points within a time window (inclusive). */
    List<OrderEntity> findByOrderPointIdInAndCreatedAtBetween(
            List<UUID> orderPointIds, LocalDateTime start, LocalDateTime end);
}
