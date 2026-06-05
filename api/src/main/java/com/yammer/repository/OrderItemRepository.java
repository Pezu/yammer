package com.yammer.repository;

import com.yammer.entity.OrderItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    List<OrderItemEntity> findByOrderIdIn(List<UUID> orderIds);

    List<OrderItemEntity> findByPaymentId(UUID paymentId);

    List<OrderItemEntity> findByPaymentIdIn(Collection<UUID> paymentIds);
}
