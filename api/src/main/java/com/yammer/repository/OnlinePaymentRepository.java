package com.yammer.repository;

import com.yammer.entity.OnlinePaymentEntity;
import com.yammer.entity.OnlinePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnlinePaymentRepository extends JpaRepository<OnlinePaymentEntity, UUID> {

    /** Stale intents to expire: still PENDING and created before the cutoff. */
    List<OnlinePaymentEntity> findByStatusAndCreatedAtBefore(
            OnlinePaymentStatus status, LocalDateTime cutoff);
}
