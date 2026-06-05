package com.yammer.repository;

import com.yammer.entity.LocationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<LocationEntity, UUID> {

    List<LocationEntity> findByClientIdOrderByName(UUID clientId);
}
