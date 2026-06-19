package com.yammer.dto;

import com.yammer.entity.CustomerEntity;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        String phone,
        String email) {

    public static CustomerResponse from(CustomerEntity e) {
        return new CustomerResponse(e.getId(), e.getFirstName(), e.getLastName(), e.getPhone(), e.getEmail());
    }
}
