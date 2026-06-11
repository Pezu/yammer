package com.yammer.service;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.UserEntity;
import com.yammer.event.OrderChangedEvent;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes order events over WebSocket to the service users responsible for the order —
 * i.e. those assigned (Assign menu) to the service point its order point routes to.
 * This keeps each service receiving only its own orders.
 *
 * <p>Triggered by {@link OrderChangedEvent} AFTER_COMMIT (and asynchronously), so clients are
 * never told about an order that later rolls back and the push never blocks the order request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final OrderPointRepository orderPointRepository;
    private final OrderPointAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final OrderWsHandler wsHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        notify(event.order(), event.type());
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
