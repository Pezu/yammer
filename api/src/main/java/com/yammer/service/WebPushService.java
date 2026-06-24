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

    /** Push a notification to every subscription of {@code username}; prune ones the service rejects. */
    @Transactional
    public void sendToUser(String username, String title, String body, String url) {
        if (pushService == null || username == null) {
            log.info("Web push skipped (enabled={}, user={})", pushService != null, username);
            return;
        }
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", url));
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

    /**
     * When an order at a pay-later order point is marked READY, notify every waiter assigned to that
     * order point (Configuration → Assign), so any assigned waiter can deliver it.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        if (!"ORDER_READY".equals(event.type())) {
            return;
        }
        OrderEntity order = event.order();
        OrderPointEntity op = orderPointRepository.findById(order.getOrderPointId()).orElse(null);
        if (op == null || !op.isPayLater()) {
            return; // only pay-later order points notify their assigned waiters
        }
        String parent = OrderPointAssignmentService.parentOf(op.getName());
        List<OrderPointAssignmentEntity> assignments =
                assignmentRepository.findByLocationIdAndParentName(op.getLocationId(), parent);
        if (assignments.isEmpty()) {
            log.info("ORDER_READY #{} at {} — no waiters assigned", order.getOrderNo(), op.getName());
            return;
        }
        Set<UUID> userIds = assignments.stream()
                .map(OrderPointAssignmentEntity::getUserId)
                .collect(Collectors.toSet());
        List<String> usernames = userRepository.findAllById(userIds).stream()
                .map(UserEntity::getUsername)
                .toList();
        log.info("ORDER_READY #{} at {} → notifying assigned waiters {}",
                order.getOrderNo(), op.getName(), usernames);
        String title = "Order ready";
        String body = "Order #" + order.getOrderNo() + " at " + op.getName() + " is ready.";
        for (String username : usernames) {
            sendToUser(username, title, body, "/waiter/orders");
        }
    }
}
