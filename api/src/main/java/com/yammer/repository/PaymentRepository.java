package com.yammer.repository;

import com.yammer.entity.PaymentEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    List<PaymentEntity> findByOrderPointIdOrderByCreatedAtDesc(UUID orderPointId);

    List<PaymentEntity> findByOrderPointIdIn(List<UUID> orderPointIds);

    /** Payments for the given points within a time window (inclusive). */
    List<PaymentEntity> findByOrderPointIdInAndCreatedAtBetween(
            List<UUID> orderPointIds, LocalDateTime start, LocalDateTime end);
}
