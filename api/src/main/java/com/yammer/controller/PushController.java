package com.yammer.controller;

import com.yammer.config.WebPushProperties;
import com.yammer.dto.PushSubscribeRequest;
import com.yammer.entity.PushSubscriptionEntity;
import com.yammer.repository.PushSubscriptionRepository;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Web Push subscription management for the waiter PWA. */
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {

    private final WebPushProperties props;
    private final PushSubscriptionRepository subscriptions;
    private final CurrentUserProvider currentUser;

    /** VAPID public key the browser needs to subscribe (empty when push is disabled). */
    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", props.isEnabled() ? props.getPublicKey() : "");
    }

    /** Store (or refresh) the current user's push subscription. */
    @PostMapping("/subscribe")
    @Transactional
    public void subscribe(@RequestBody PushSubscribeRequest request) {
        if (request.endpoint() == null || request.endpoint().isBlank()
                || request.keys() == null
                || request.keys().p256dh() == null || request.keys().auth() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription");
        }
        UserPrincipal me = currentUser.require();
        PushSubscriptionEntity sub = subscriptions.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscriptionEntity::new);
        sub.setUsername(me.username());
        sub.setEndpoint(request.endpoint());
        sub.setP256dh(request.keys().p256dh());
        sub.setAuth(request.keys().auth());
        if (sub.getCreatedAt() == null) {
            sub.setCreatedAt(LocalDateTime.now());
        }
        subscriptions.save(sub);
    }

    /** Remove a subscription (e.g. when the user disables notifications). */
    @PostMapping("/unsubscribe")
    @Transactional
    public void unsubscribe(@RequestBody PushSubscribeRequest request) {
        if (request.endpoint() != null && !request.endpoint().isBlank()) {
            subscriptions.deleteByEndpoint(request.endpoint());
        }
    }
}
