package com.yammer.repository;

import com.yammer.entity.EventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    List<EventEntity> findByLocationIdOrderByStartDateDesc(UUID locationId);
}
