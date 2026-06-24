package com.yammer.repository;

import com.yammer.entity.CustomerEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    /** First customer with this prefix + phone — the self-service lookup / de-dupe key. */
    Optional<CustomerEntity> findFirstByPrefixAndPhone(String prefix, String phone);
}
