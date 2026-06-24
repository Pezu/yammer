package com.yammer.repository;

import com.yammer.entity.PushSubscriptionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, UUID> {

    List<PushSubscriptionEntity> findByUsername(String username);

    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);
}
