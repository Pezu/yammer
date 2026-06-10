package com.yammer.repository;

import com.yammer.entity.FiscalStatus;
import com.yammer.entity.PaymentEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    List<PaymentEntity> findByOrderPointIdOrderByCreatedAtDesc(UUID orderPointId);

    List<PaymentEntity> findByOrderPointIdIn(List<UUID> orderPointIds);

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
