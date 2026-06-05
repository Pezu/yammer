package com.yammer.repository;

import com.yammer.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<UserEntity> findByClientIdOrderByUsername(UUID clientId);
}
