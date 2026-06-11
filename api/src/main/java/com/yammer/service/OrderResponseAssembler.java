package com.yammer.service;

import com.yammer.dto.OrderResponse;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.repository.OrderItemRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds {@link OrderResponse}s from a set of orders: loads their items in one query, groups by
 * order, and attaches the order-point name. Shared by the order list, the service board and the
 * waiter board so the grouping/mapping lives in exactly one place.
 */
@Component
@RequiredArgsConstructor
public class OrderResponseAssembler {

    private final OrderItemRepository orderItemRepository;

    /** Assemble responses for {@code orders}, resolving each order point's display name via {@code opNames}. */
    public List<OrderResponse> assemble(List<OrderEntity> orders, Map<UUID, String> opNames) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        return orders.stream()
                .map(o -> OrderResponse.from(
                        o, itemsByOrder.getOrDefault(o.getId(), List.of()), opNames.get(o.getOrderPointId())))
                .toList();
    }
}
