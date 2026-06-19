package com.yammer.repository;

import com.yammer.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    List<OrderItemEntity> findByOrderIdIn(List<UUID> orderIds);

    /** Only the unsettled lines of the given orders — avoids loading paid history that's discarded. */
    List<OrderItemEntity> findByOrderIdInAndPaymentIdIsNull(List<UUID> orderIds);

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

    /** Same aggregation, scoped to one event (uses the orders' indexed {@code event_id}). */
    @Query("select i.name as name, sum(i.quantity) as quantity from OrderItemEntity i "
            + "where i.orderId in (select o.id from OrderEntity o where o.eventId = :eventId) "
            + "group by i.name order by sum(i.quantity) desc, i.name asc")
    List<ProductQuantity> aggregateProductQuantitiesByEvent(@Param("eventId") UUID eventId);

    /** Per order point: line counts and totals — for the waiter tables list/stats (no row load). */
    interface OrderPointItemAgg {
        UUID getOpId();
        long getItems();
        long getUnpaidCount();
        BigDecimal getOrdered();
        BigDecimal getUnpaid();
        BigDecimal getSettled();
    }

    @Query(nativeQuery = true, value = """
            select o.order_point_id as "opId",
              count(i.id) as "items",
              coalesce(sum(case when i.payment_id is null then 1 else 0 end),0) as "unpaidCount",
              coalesce(sum(i.price*i.quantity),0) as "ordered",
              coalesce(sum(case when i.payment_id is null then i.price*i.quantity else 0 end),0) as "unpaid",
              coalesce(sum(case when i.payment_id is not null then i.price*i.quantity else 0 end),0) as "settled"
            from order_item i
            join orders o on o.id = i.order_id
            where o.order_point_id in :opIds
            group by o.order_point_id
            """)
    List<OrderPointItemAgg> aggregateByOrderPoint(@Param("opIds") Collection<UUID> opIds);

    /** One order point's bill aggregated per product: paid vs unpaid quantity (the table screen). */
    interface BillLineRow {
        UUID getMenuItemId();
        String getName();
        BigDecimal getPrice();
        long getPaidQty();
        long getUnpaidQty();
    }

    @Query(nativeQuery = true, value = """
            select i.menu_item_id as "menuItemId",
              min(i.name) as "name",
              min(i.price) as "price",
              coalesce(sum(case when i.payment_id is not null then i.quantity else 0 end),0) as "paidQty",
              coalesce(sum(case when i.payment_id is null then i.quantity else 0 end),0) as "unpaidQty"
            from order_item i
            join orders o on o.id = i.order_id
            where o.order_point_id = :opId and i.menu_item_id is not null
            group by i.menu_item_id
            order by min(i.name)
            """)
    List<BillLineRow> billByOrderPoint(@Param("opId") UUID opId);
}
