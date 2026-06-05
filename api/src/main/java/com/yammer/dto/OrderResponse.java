package com.yammer.dto;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID orderPointId,
        String orderPointName,
        String createdBy,
        LocalDateTime createdAt,
        String status,
        List<OrderItemResponse> items,
        BigDecimal total) {

    public record OrderItemResponse(
            UUID id, UUID menuItemId, String name, BigDecimal price, int quantity, boolean paid) {
        public static OrderItemResponse from(OrderItemEntity e) {
            return new OrderItemResponse(
                    e.getId(),
                    e.getMenuItemId(),
                    e.getName(),
                    e.getPrice(),
                    e.getQuantity(),
                    e.getPaymentId() != null);
        }
    }

    public static OrderResponse from(OrderEntity order, List<OrderItemEntity> items, String orderPointName) {
        BigDecimal total = items.stream()
                .map(i -> (i.getPrice() == null ? BigDecimal.ZERO : i.getPrice())
                        .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(
                order.getId(),
                order.getOrderPointId(),
                orderPointName,
                order.getCreatedBy(),
                order.getCreatedAt(),
                order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList(),
                total);
    }
}
