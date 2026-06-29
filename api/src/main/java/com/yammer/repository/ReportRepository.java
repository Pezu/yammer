package com.yammer.repository;

import com.yammer.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dashboard reports aggregated in the database (SUM / GROUP BY) so each call returns a handful of
 * rows instead of loading the full event into the JVM. All queries are event-scoped: the dashboard
 * is per-event, so {@code event_id} (indexed) is the single filter. Money is line total
 * {@code price * quantity}; an order line is paid iff it has a {@code payment_id}, protocol-settled
 * iff that payment's method is {@code PROTOCOL}.
 */
public interface ReportRepository extends Repository<OrderItemEntity, UUID> {

    // ─── summary (one row) ───────────────────────────────────────────────────

    interface SummaryRow {
        BigDecimal getTotalSales();
        BigDecimal getTotalPaid();
        BigDecimal getTotalProtocol();
        BigDecimal getRemainingToPay();
        BigDecimal getRemainingProtocol();
    }

    @Query(nativeQuery = true, value = """
            select
              coalesce(sum(i.price*i.quantity),0) as "totalSales",
              coalesce(sum(case when i.payment_id is not null and p.method<>'PROTOCOL'
                                 then i.price*i.quantity else 0 end),0) as "totalPaid",
              coalesce(sum(case when p.method='PROTOCOL' then i.price*i.quantity else 0 end),0) as "totalProtocol",
              coalesce(sum(case when i.payment_id is null and not op.protocol
                                 then i.price*i.quantity else 0 end),0) as "remainingToPay",
              coalesce(sum(case when i.payment_id is null and op.protocol
                                 then i.price*i.quantity else 0 end),0) as "remainingProtocol"
            from order_item i
            join orders o on o.id = i.order_id
            join order_point op on op.id = o.order_point_id
            left join payment p on p.id = i.payment_id
            where o.event_id = :eventId
            """)
    SummaryRow summaryByEvent(@Param("eventId") UUID eventId);

    // ─── tables (per order point) ────────────────────────────────────────────

    interface TableItemRow {
        UUID getOpId();
        String getOpName();
        boolean getProtocolPoint();
        BigDecimal getOrdered();
        BigDecimal getProtocolSettled();
        BigDecimal getRemaining();
        BigDecimal getRemainingProtocol();
    }

    @Query(nativeQuery = true, value = """
            select op.id as "opId", op.name as "opName", op.protocol as "protocolPoint",
              coalesce(sum(i.price*i.quantity),0) as "ordered",
              coalesce(sum(case when p.method='PROTOCOL' then i.price*i.quantity else 0 end),0) as "protocolSettled",
              coalesce(sum(case when i.payment_id is null and not op.protocol
                                 then i.price*i.quantity else 0 end),0) as "remaining",
              coalesce(sum(case when i.payment_id is null and op.protocol
                                 then i.price*i.quantity else 0 end),0) as "remainingProtocol"
            from order_item i
            join orders o on o.id = i.order_id
            join order_point op on op.id = o.order_point_id
            left join payment p on p.id = i.payment_id
            where o.event_id = :eventId
            group by op.id, op.name, op.protocol
            """)
    List<TableItemRow> tableItemsByEvent(@Param("eventId") UUID eventId);

    interface TablePayRow {
        UUID getOpId();
        BigDecimal getCash();
        BigDecimal getCard();
    }

    @Query(nativeQuery = true, value = """
            select p.order_point_id as "opId",
              coalesce(sum(case when p.method='CASH' then p.amount else 0 end),0) as "cash",
              coalesce(sum(case when p.method='CARD' then p.amount else 0 end),0) as "card"
            from payment p
            where p.event_id = :eventId
            group by p.order_point_id
            """)
    List<TablePayRow> tablePaymentsByEvent(@Param("eventId") UUID eventId);

    // ─── waiters ─────────────────────────────────────────────────────────────

    interface WaiterRow {
        String getWaiter();
        long getOrders();
        BigDecimal getSales();
        BigDecimal getUnsettledPaid();
        BigDecimal getUnsettledProtocol();
    }

    @Query(nativeQuery = true, value = """
            select coalesce(nullif(trim(o.created_by),''),'—') as "waiter",
              count(distinct o.id) as "orders",
              coalesce(sum(i.price*i.quantity),0) as "sales",
              coalesce(sum(case when i.payment_id is null and not op.protocol
                                 then i.price*i.quantity else 0 end),0) as "unsettledPaid",
              coalesce(sum(case when i.payment_id is null and op.protocol
                                 then i.price*i.quantity else 0 end),0) as "unsettledProtocol"
            from orders o
            join order_point op on op.id = o.order_point_id
            left join order_item i on i.order_id = o.id
            where o.event_id = :eventId
            group by coalesce(nullif(trim(o.created_by),''),'—')
            order by coalesce(sum(i.price*i.quantity),0) desc
            """)
    List<WaiterRow> waitersByEvent(@Param("eventId") UUID eventId);

    // ─── waiter × order point ────────────────────────────────────────────────

    interface WaiterTableRowAgg {
        String getWaiter();
        UUID getOpId();
        String getOpName();
        BigDecimal getOrdered();
        BigDecimal getPaid();
        BigDecimal getProtocolSettled();
    }

    @Query(nativeQuery = true, value = """
            select coalesce(nullif(trim(o.created_by),''),'—') as "waiter",
              op.id as "opId", op.name as "opName",
              coalesce(sum(i.price*i.quantity),0) as "ordered",
              coalesce(sum(case when i.payment_id is not null and p.method<>'PROTOCOL'
                                 then i.price*i.quantity else 0 end),0) as "paid",
              coalesce(sum(case when p.method='PROTOCOL' then i.price*i.quantity else 0 end),0) as "protocolSettled"
            from order_item i
            join orders o on o.id = i.order_id
            join order_point op on op.id = o.order_point_id
            left join payment p on p.id = i.payment_id
            where o.event_id = :eventId
            group by coalesce(nullif(trim(o.created_by),''),'—'), op.id, op.name
            order by coalesce(nullif(trim(o.created_by),''),'—') asc, coalesce(sum(i.price*i.quantity),0) desc
            """)
    List<WaiterTableRowAgg> waiterTablesByEvent(@Param("eventId") UUID eventId);

    // ─── ordered items (per waiter × order point × product) ──────────────────

    interface TableItemDetailRow {
        String getWaiter();
        String getTableName();
        String getName();
        BigDecimal getPrice();
        long getQuantity();
        BigDecimal getTotal();
    }

    /**
     * Every ordered line for the event, grouped by waiter, order point and product (same product at
     * a different price stays a separate row). Backs the dashboard drill-downs: the Tables modal sums
     * these across waiters per order point, the Waiters widget reads them per waiter × order point.
     */
    @Query(nativeQuery = true, value = """
            select coalesce(nullif(trim(o.created_by),''),'—') as "waiter",
              op.name as "tableName",
              i.name as "name",
              coalesce(i.price,0) as "price",
              sum(i.quantity) as "quantity",
              coalesce(sum(i.price*i.quantity),0) as "total"
            from order_item i
            join orders o on o.id = i.order_id
            join order_point op on op.id = o.order_point_id
            where o.event_id = :eventId
            group by coalesce(nullif(trim(o.created_by),''),'—'), op.name, i.name, coalesce(i.price,0)
            order by op.name, coalesce(nullif(trim(o.created_by),''),'—'), i.name
            """)
    List<TableItemDetailRow> tableItemDetailsByEvent(@Param("eventId") UUID eventId);

    interface WaiterTableTipRow {
        String getWaiter();
        UUID getOpId();
        BigDecimal getTip();
    }

    /** Tip is per-payment, so sum over the DISTINCT non-protocol payments behind each (waiter, op). */
    @Query(nativeQuery = true, value = """
            select t.waiter as "waiter", t.op_id as "opId", coalesce(sum(t.tip),0) as "tip"
            from (
              select distinct coalesce(nullif(trim(o.created_by),''),'—') as waiter,
                     o.order_point_id as op_id, p.id as pid, p.tip as tip
              from order_item i
              join orders o on o.id = i.order_id
              join payment p on p.id = i.payment_id and p.method <> 'PROTOCOL'
              where o.event_id = :eventId
            ) t
            group by t.waiter, t.op_id
            """)
    List<WaiterTableTipRow> waiterTableTipsByEvent(@Param("eventId") UUID eventId);

    // ─── sales over time (10-minute buckets) ─────────────────────────────────

    interface BucketOrderedRow {
        LocalDateTime getBucket();
        BigDecimal getOrdered();
        long getCnt();
    }

    @Query(nativeQuery = true, value = """
            select date_bin(interval '10 minutes', o.created_at, timestamp '2000-01-01') as "bucket",
              coalesce(sum(i.price*i.quantity),0) as "ordered",
              count(distinct o.id) as "cnt"
            from orders o
            left join order_item i on i.order_id = o.id
            where o.event_id = :eventId
            group by 1
            """)
    List<BucketOrderedRow> orderedByBucketForEvent(@Param("eventId") UUID eventId);

    interface BucketValueRow {
        LocalDateTime getBucket();
        BigDecimal getValue();
    }

    /** Paid (non-protocol) per bucket, by payment time and amount. */
    @Query(nativeQuery = true, value = """
            select date_bin(interval '10 minutes', p.created_at, timestamp '2000-01-01') as "bucket",
              coalesce(sum(p.amount),0) as "value"
            from payment p
            where p.event_id = :eventId and p.method <> 'PROTOCOL'
            group by 1
            """)
    List<BucketValueRow> paidByBucketForEvent(@Param("eventId") UUID eventId);

    /** Protocol-settled per bucket, by payment time; value is the line totals the payment covers. */
    @Query(nativeQuery = true, value = """
            select date_bin(interval '10 minutes', p.created_at, timestamp '2000-01-01') as "bucket",
              coalesce(sum(i.price*i.quantity),0) as "value"
            from payment p
            join order_item i on i.payment_id = p.id
            where p.event_id = :eventId and p.method = 'PROTOCOL'
            group by 1
            """)
    List<BucketValueRow> protocolByBucketForEvent(@Param("eventId") UUID eventId);

    // ─── final report (per user + order point: paid/tip by method) ─────────────

    interface FinalReportRow {
        String getUserName();
        String getTableName();
        BigDecimal getPaidCard();
        BigDecimal getPaidCash();
        BigDecimal getTipCard();
        BigDecimal getTipCash();
    }

    /**
     * Per user (payment.created_by, shown as users.name when set) and order point: card/cash paid
     * and tip. Protocol is excluded so the totals match the dashboard's "paid" cash + card.
     */
    @Query(nativeQuery = true, value = """
            select coalesce(nullif(trim(u.name),''), p.created_by, '—') as "userName",
              op.name as "tableName",
              coalesce(sum(case when p.method='CARD' then p.amount else 0 end),0) as "paidCard",
              coalesce(sum(case when p.method='CASH' then p.amount else 0 end),0) as "paidCash",
              coalesce(sum(case when p.method='CARD' then p.tip    else 0 end),0) as "tipCard",
              coalesce(sum(case when p.method='CASH' then p.tip    else 0 end),0) as "tipCash"
            from payment p
            join order_point op on op.id = p.order_point_id
            left join users u on u.username = p.created_by
            where p.event_id = :eventId and p.method in ('CASH','CARD')
            group by coalesce(nullif(trim(u.name),''), p.created_by, '—'), op.name
            order by 1, 2
            """)
    List<FinalReportRow> finalReportByEvent(@Param("eventId") UUID eventId);
}
