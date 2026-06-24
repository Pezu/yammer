package com.yammer.dto;

/** Public lookup of a customer by dial prefix + phone (self-service pay-now flow). */
public record CustomerLookupRequest(String prefix, String phone) {
}
