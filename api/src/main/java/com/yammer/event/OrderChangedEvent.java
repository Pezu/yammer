package com.yammer.event;

import com.yammer.entity.OrderEntity;

/**
 * Published (inside the order transaction) when an order is created or delivered.
 * Consumed AFTER_COMMIT so service users are only notified about changes that actually
 * persisted, and the WebSocket push runs off the request thread.
 *
 * @param order the affected order (detached by the time the listener runs)
 * @param type  the WebSocket message type, e.g. {@code ORDER_CREATED} / {@code ORDER_DELIVERED}
 */
public record OrderChangedEvent(OrderEntity order, String type) {
}
