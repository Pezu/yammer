package com.yammer.repository;

import com.yammer.entity.VatTypeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VatTypeRepository extends JpaRepository<VatTypeEntity, UUID> {
}
