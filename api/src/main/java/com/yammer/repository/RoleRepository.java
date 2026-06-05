package com.yammer.repository;

import com.yammer.entity.RoleEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    boolean existsByRoleIgnoreCase(String role);
}
