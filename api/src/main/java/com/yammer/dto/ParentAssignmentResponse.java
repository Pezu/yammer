package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/** One order-point parent (e.g. "B1", "M80") and the users assigned to it. */
public record ParentAssignmentResponse(String parentName, List<UUID> userIds) {
}
