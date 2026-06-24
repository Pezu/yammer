package com.yammer.dto;

import java.util.UUID;

/** Result of a customer lookup: the customer id when matched, else null (ask for details). */
public record CustomerLookupResponse(UUID customerId) {
}
