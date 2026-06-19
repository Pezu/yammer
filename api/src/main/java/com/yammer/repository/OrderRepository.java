package com.yammer.repository;

import com.yammer.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByOrderPointIdInOrderByCreatedAtDesc(List<UUID> orderPointIds);

    /** One page of orders for the given points (used by the paginated /orders/page endpoint). */
    Page<OrderEntity> findByOrderPointIdIn(Collection<UUID> orderPointIds, Pageable pageable);

    /** One page of orders for a single event (SUPER, unscoped by order point). */
    Page<OrderEntity> findByEventId(UUID eventId, Pageable pageable);

    /** One page of the given points' orders, restricted to a single event. */
    Page<OrderEntity> findByOrderPointIdInAndEventId(
            Collection<UUID> orderPointIds, UUID eventId, Pageable pageable);

    List<OrderEntity> findByOrderPointIdOrderByCreatedAtAsc(UUID orderPointId);

    /**
     * Orders for the given points restricted to a set of statuses (newest first). Lets the
     * service/waiter boards fetch only ORDERED/READY rows instead of loading full history and
     * filtering terminal (DELIVERED/CANCELED) orders out in memory.
     */
    List<OrderEntity> findByOrderPointIdInAndStatusInOrderByCreatedAtDesc(
            Collection<UUID> orderPointIds, Collection<String> statuses);

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
