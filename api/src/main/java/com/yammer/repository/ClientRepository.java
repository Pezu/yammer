package com.yammer.repository;

import com.yammer.entity.ClientEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {
}
