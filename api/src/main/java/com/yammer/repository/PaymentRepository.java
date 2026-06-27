package com.yammer.repository;

import com.yammer.entity.FiscalStatus;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository
        extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {

    /** Distinct payment methods present for an event — for the payments-report filter combo. */
    @Query("select distinct p.method from PaymentEntity p where p.eventId = :eventId")
    List<PaymentMethod> distinctMethodsByEvent(@Param("eventId") UUID eventId);

    /** Distinct users (createdBy) present for an event. */
    @Query("select distinct p.createdBy from PaymentEntity p "
            + "where p.eventId = :eventId and p.createdBy is not null")
    List<String> distinctUsersByEvent(@Param("eventId") UUID eventId);

    List<PaymentEntity> findByOrderPointIdOrderByCreatedAtDesc(UUID orderPointId);

    /** Per order point: paid (cash/card) and tip totals — for the waiter tables stats (no row load). */
    interface OrderPointPaymentAgg {
        UUID getOpId();
        BigDecimal getPaidCard();
        BigDecimal getPaidCash();
        BigDecimal getTipCard();
        BigDecimal getTipCash();
    }

    @Query(nativeQuery = true, value = """
            select order_point_id as "opId",
              coalesce(sum(case when method='CARD' then amount else 0 end),0) as "paidCard",
              coalesce(sum(case when method='CASH' then amount else 0 end),0) as "paidCash",
              coalesce(sum(case when method='CARD' then tip else 0 end),0) as "tipCard",
              coalesce(sum(case when method='CASH' then tip else 0 end),0) as "tipCash"
            from payment
            where order_point_id in :opIds
            group by order_point_id
            """)
    List<OrderPointPaymentAgg> aggregateByOrderPoint(@Param("opIds") Collection<UUID> opIds);

    /** Same as {@link #aggregateByOrderPoint} but only the given user's own payments. */
    @Query(nativeQuery = true, value = """
            select order_point_id as "opId",
              coalesce(sum(case when method='CARD' then amount else 0 end),0) as "paidCard",
              coalesce(sum(case when method='CASH' then amount else 0 end),0) as "paidCash",
              coalesce(sum(case when method='CARD' then tip else 0 end),0) as "tipCard",
              coalesce(sum(case when method='CASH' then tip else 0 end),0) as "tipCash"
            from payment
            where order_point_id in :opIds and created_by = :createdBy
            group by order_point_id
            """)
    List<OrderPointPaymentAgg> aggregateByOrderPointForUser(
            @Param("opIds") Collection<UUID> opIds, @Param("createdBy") String createdBy);

    List<PaymentEntity> findByOrderPointIdIn(List<UUID> orderPointIds);

    /** Payments at the given points excluding one method (e.g. PROTOCOL), newest first — in SQL. */
    List<PaymentEntity> findByOrderPointIdInAndMethodNotOrderByCreatedAtDesc(
            List<UUID> orderPointIds, PaymentMethod method);

    /** Payments for the given points within a time window (inclusive). */
    List<PaymentEntity> findByOrderPointIdInAndCreatedAtBetween(
            List<UUID> orderPointIds, LocalDateTime start, LocalDateTime end);

    /**
     * Fiscal outbox query: payments still in {@code status} (PENDING) that either were
     * never sent ({@code fiscal_sent_at} is null) or were sent before {@code cutoff}
     * and never acknowledged (presumed lost while the bridge was offline/reconnecting).
     */
    @Query("select p from PaymentEntity p where p.fiscalStatus = :status "
            + "and (p.fiscalSentAt is null or p.fiscalSentAt < :cutoff) "
            + "order by p.createdAt asc")
    List<PaymentEntity> findFiscalDispatchable(@Param("status") FiscalStatus status,
                                               @Param("cutoff") LocalDateTime cutoff);
}
