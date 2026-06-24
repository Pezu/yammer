package com.yammer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.config.WebPushProperties;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PushSubscriptionEntity;
import com.yammer.entity.UserEntity;
import com.yammer.event.OrderChangedEvent;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PushSubscriptionRepository;
import com.yammer.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends Web Push (VAPID) OS notifications to the waiter PWA. The push is delivered by the device's
 * push service (FCM/APNs) and shown by the service worker even when the app is closed. Disabled
 * (no-op) when no VAPID keys are configured. Fires an "order ready" push to every waiter assigned
 * (backoffice → Configuration → Assign) to a pay-later order point when one of its orders is READY.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final WebPushProperties props;
    private final PushSubscriptionRepository subscriptions;
    private final OrderPointRepository orderPointRepository;
    private final OrderPointAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private PushService pushService;

    @PostConstruct
    void init() {
        if (!props.isEnabled()) {
            log.info("Web Push disabled — no VAPID keys configured.");
            return;
        }
        Security.addProvider(new BouncyCastleProvider());
        try {
            pushService = new PushService(props.getPublicKey(), props.getPrivateKey(), props.getSubject());
            log.info("Web Push enabled.");
        } catch (Exception e) {
            log.error("Failed to initialize Web Push: {}", e.getMessage(), e);
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    /** Push a JSON payload to every subscription of {@code username}; prune ones the service rejects. */
    @Transactional
    public void sendToUser(String username, Map<String, Object> data) {
        if (pushService == null || username == null) {
            log.info("Web push skipped (enabled={}, user={})", pushService != null, username);
            return;
        }
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return;
        }
        List<PushSubscriptionEntity> subs = subscriptions.findByUsername(username);
        log.info("Web push to '{}': {} subscription(s)", username, subs.size());
        for (PushSubscriptionEntity sub : subs) {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(), sub.getP256dh(), sub.getAuth(),
                        payload.getBytes(StandardCharsets.UTF_8));
                int code = pushService.send(notification).getStatusLine().getStatusCode();
                log.info("Web push to '{}' -> status {}", username, code);
                if (code == 404 || code == 410) {
                    subscriptions.delete(sub); // subscription gone — stop trying it
                }
            } catch (Exception e) {
                log.warn("Web push to {} failed: {}", username, e.getMessage(), e);
            }
        }
    }

    private static final String ORDERS_TAG = "waiter-orders";

    /**
     * Push to every waiter assigned (Configuration → Assign) to a pay-later order point when one of
     * its orders changes: READY adds a stacked "order ready" notification; DELIVERED sends a remove
     * message so the same order's line drops out of that stack on every assigned device.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        boolean ready = "ORDER_READY".equals(event.type());
        boolean delivered = "ORDER_DELIVERED".equals(event.type());
        if (!ready && !delivered) {
            return;
        }
        OrderEntity order = event.order();
        OrderPointEntity op = orderPointRepository.findById(order.getOrderPointId()).orElse(null);
        if (op == null || !op.isPayLater()) {
            return; // only pay-later order points notify their assigned waiters
        }
        List<String> usernames = assignedWaiters(op);
        if (usernames.isEmpty()) {
            return;
        }
        Map<String, Object> payload = ready
                ? Map.of(
                        "type", "ready",
                        "tag", ORDERS_TAG,
                        "orderNo", order.getOrderNo(),
                        "title", "Order ready",
                        "body", "Order #" + order.getOrderNo() + " at " + op.getName() + " is ready.",
                        "url", "/waiter/orders")
                : Map.of(
                        "type", "remove",
                        "tag", ORDERS_TAG,
                        "orderNo", order.getOrderNo(),
                        "url", "/waiter/orders");
        log.info("{} #{} at {} → {} assigned waiters {}",
                event.type(), order.getOrderNo(), op.getName(), ready ? "notifying" : "clearing for", usernames);
        for (String username : usernames) {
            sendToUser(username, payload);
        }
    }

    /** Usernames of all waiters assigned to the order point's parent group at its location. */
    private List<String> assignedWaiters(OrderPointEntity op) {
        String parent = OrderPointAssignmentService.parentOf(op.getName());
        List<OrderPointAssignmentEntity> assignments =
                assignmentRepository.findByLocationIdAndParentName(op.getLocationId(), parent);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<UUID> userIds = assignments.stream()
                .map(OrderPointAssignmentEntity::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findAllById(userIds).stream()
                .map(UserEntity::getUsername)
                .toList();
    }
}
