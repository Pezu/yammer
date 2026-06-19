package com.yammer.dto;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        Long orderNo,
        UUID orderPointId,
        String orderPointName,
        UUID eventId,
        String createdBy,
        LocalDateTime createdAt,
        String status,
        List<OrderItemResponse> items,
        BigDecimal total,
        /** Payment state of the order's lines: NOT / PAR / PAID. */
        String paid) {

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
        long paidCount = items.stream().filter(i -> i.getPaymentId() != null).count();
        String paid = items.isEmpty() || paidCount == 0
                ? "NOT"
                : paidCount == items.size() ? "PAID" : "PAR";
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getOrderPointId(),
                orderPointName,
                order.getEventId(),
                order.getCreatedBy(),
                order.getCreatedAt(),
                order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList(),
                total,
                paid);
    }
}
