package com.yammer.service;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.UserRepository;
import com.yammer.ws.OrderWsHandler;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Pushes order events over WebSocket to the service users responsible for the order —
 * i.e. those assigned (Assign menu) to the service point its order point routes to.
 * This keeps each service receiving only its own orders.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final OrderPointRepository orderPointRepository;
    private final OrderPointAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final OrderWsHandler wsHandler;

    public void orderCreated(OrderEntity order) {
        notify(order, "ORDER_CREATED");
    }

    public void orderDelivered(OrderEntity order) {
        notify(order, "ORDER_DELIVERED");
    }

    private void notify(OrderEntity order, String type) {
        try {
            OrderPointEntity table = orderPointRepository.findById(order.getOrderPointId()).orElse(null);
            if (table == null || table.getServiceOrderPointId() == null) {
                return; // no service point configured → nobody to notify
            }
            OrderPointEntity servicePoint =
                    orderPointRepository.findById(table.getServiceOrderPointId()).orElse(null);
            if (servicePoint == null) {
                return;
            }
            String parent = OrderPointAssignmentService.parentOf(servicePoint.getName());
            List<OrderPointAssignmentEntity> assignments = assignmentRepository
                    .findByLocationIdAndParentName(servicePoint.getLocationId(), parent);
            if (assignments.isEmpty()) {
                return;
            }
            Set<UUID> userIds = assignments.stream()
                    .map(OrderPointAssignmentEntity::getUserId)
                    .collect(Collectors.toSet());
            List<String> usernames = userRepository.findAllById(userIds).stream()
                    .map(UserEntity::getUsername)
                    .toList();
            String payload = "{\"type\":\"" + type + "\",\"orderId\":\"" + order.getId() + "\"}";
            wsHandler.sendToUsers(usernames, payload);
        } catch (Exception e) {
            // a failed push must never break the order operation
            log.warn("Failed to push {} for order {}: {}", type, order.getId(), e.getMessage());
        }
    }
}
