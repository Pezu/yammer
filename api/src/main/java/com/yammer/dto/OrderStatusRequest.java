package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for moving an order to a new kanban status. */
public record OrderStatusRequest(@NotBlank String status) {
}
