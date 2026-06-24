package com.yammer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VAPID keys for Web Push (waiter PWA OS notifications), bound from {@code webpush.*}.
 * When the keys are blank, push is disabled (no subscriptions accepted, nothing sent).
 */
@Component
@ConfigurationProperties(prefix = "webpush")
@Getter
@Setter
public class WebPushProperties {

    /** VAPID public key (base64url) — also handed to the browser to subscribe. */
    private String publicKey;

    /** VAPID private key (base64url) — secret. */
    private String privateKey;

    /** VAPID subject: a mailto: or https: contact URL for the push service. */
    private String subject = "mailto:admin@yammer.app";

    public boolean isEnabled() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
