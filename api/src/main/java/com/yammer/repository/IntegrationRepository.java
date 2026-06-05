package com.yammer.repository;

import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.IntegrationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<IntegrationEntity, UUID> {

    List<IntegrationEntity> findByLocationIdOrderByName(UUID locationId);

    List<IntegrationEntity> findByLocationIdAndTypeOrderByName(UUID locationId, IntegrationType type);
}
