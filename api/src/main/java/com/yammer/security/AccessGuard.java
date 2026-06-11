package com.yammer.security;

import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single home for the multi-tenant access rule: a non-SUPER caller may only touch rows
 * that belong to their own client. The rule was previously copy-pasted across ~9 services;
 * centralising it removes the risk that one copy drifts and opens a tenant-isolation hole.
 *
 * <p>Every "not accessible" case is reported as a 404 (never 403) so the API does not reveal
 * whether an id exists in another tenant.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final CurrentUserProvider currentUser;
    private final LocationRepository locationRepository;
    private final OrderPointRepository orderPointRepository;
    private final OrderRepository orderRepository;

    /** The location, or 404 if it doesn't exist or belongs to another client. */
    public LocationEntity requireAccessibleLocation(UUID locationId) {
        LocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> notFound("Location", locationId));
        assertSameClient(location.getClientId(), "Location", locationId);
        return location;
    }

    /** The order point, or 404 if it (or its location) doesn't exist or belongs to another client. */
    public OrderPointEntity requireAccessibleOrderPoint(UUID orderPointId) {
        OrderPointEntity op = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> notFound("Order point", orderPointId));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound("Order point", orderPointId));
        assertSameClient(location.getClientId(), "Order point", orderPointId);
        return op;
    }

    /** The order, or 404 if it (or its order point's location) isn't accessible to the caller. */
    public OrderEntity requireAccessibleOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> notFound("Order", orderId));
        OrderPointEntity op = orderPointRepository.findById(order.getOrderPointId())
                .orElseThrow(() -> notFound("Order", orderId));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound("Order", orderId));
        assertSameClient(location.getClientId(), "Order", orderId);
        return order;
    }

    /**
     * Order-point ids the caller may see: SUPER → all; otherwise every order point under the
     * caller's client. Returns an empty list when the caller is scoped to no client.
     */
    public List<UUID> visibleOrderPointIds() {
        UserPrincipal me = currentUser.require();
        if (me.isSuper()) {
            return orderPointRepository.findAllIds();
        }
        if (me.clientId() == null) {
            return List.of();
        }
        return orderPointRepository.findIdsByClientId(me.clientId());
    }

    private void assertSameClient(UUID rowClientId, String entity, UUID id) {
        UserPrincipal me = currentUser.require();
        if (!me.isSuper() && !Objects.equals(rowClientId, me.clientId())) {
            throw notFound(entity, id);
        }
    }

    private ResponseStatusException notFound(String entity, UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, entity + " not found: " + id);
    }
}
