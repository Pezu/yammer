package com.yammer.repository;

import com.yammer.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByOrderPointIdInOrderByCreatedAtDesc(List<UUID> orderPointIds);

    List<OrderEntity> findByOrderPointIdOrderByCreatedAtAsc(UUID orderPointId);

    /** Orders for the given points within a time window (inclusive). */
    List<OrderEntity> findByOrderPointIdInAndCreatedAtBetween(
            List<UUID> orderPointIds, LocalDateTime start, LocalDateTime end);

    /** Highest order number issued to the given client so far (0 when there are none). */
    @Query(value = """
            SELECT COALESCE(MAX(o.order_no), 0)
            FROM orders o
            JOIN order_point op ON op.id = o.order_point_id
            JOIN location l ON l.id = op.location_id
            WHERE l.client_id = :clientId
            """, nativeQuery = true)
    long maxOrderNoForClient(@Param("clientId") UUID clientId);
}
