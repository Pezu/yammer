package com.yammer.dto;

import java.util.UUID;

/** Where the public root should send a customer: the first non-pay-later order point of the active event. */
public record LandingResponse(UUID orderPointId) {}
