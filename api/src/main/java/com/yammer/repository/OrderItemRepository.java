package com.yammer.repository;

import com.yammer.entity.OrderItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    List<OrderItemEntity> findByOrderIdIn(List<UUID> orderIds);

    List<OrderItemEntity> findByPaymentId(UUID paymentId);

    List<OrderItemEntity> findByPaymentIdIn(Collection<UUID> paymentIds);

    /** A product name with its summed ordered quantity. */
    interface ProductQuantity {
        String getName();

        long getQuantity();
    }

    /**
     * Total ordered quantity per product name across the given order points, aggregated in the
     * database (no full order_item load). Highest quantity first, then name for a stable order.
     */
    @Query("select i.name as name, sum(i.quantity) as quantity from OrderItemEntity i "
            + "where i.orderId in (select o.id from OrderEntity o where o.orderPointId in :orderPointIds) "
            + "group by i.name order by sum(i.quantity) desc, i.name asc")
    List<ProductQuantity> aggregateProductQuantities(@Param("orderPointIds") Collection<UUID> orderPointIds);
}
