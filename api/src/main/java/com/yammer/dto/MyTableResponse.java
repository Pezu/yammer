package com.yammer.dto;

import com.yammer.entity.OrderPointEntity;
import java.util.UUID;

/**
 * An assigned order point for the waiter Tables grid, with its bill state.
 *
 * @param status EMPTY (no orders), UNPAID (something to pay) or PAID (all settled)
 */
public record MyTableResponse(
        UUID id,
        UUID locationId,
        String name,
        boolean payLater,
        boolean protocol,
        UUID menuId,
        UUID serviceOrderPointId,
        String status) {

    public static MyTableResponse from(OrderPointEntity e, String status) {
        return new MyTableResponse(
                e.getId(),
                e.getLocationId(),
                e.getName(),
                e.isPayLater(),
                e.isProtocol(),
                e.getMenuId(),
                e.getServiceOrderPointId(),
                status);
    }
}
