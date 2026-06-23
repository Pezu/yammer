package com.yammer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Netopia gateway credentials and endpoints, bound from {@code netopia.*} in application config.
 * Secrets come from the environment — never commit live keys.
 */
@Component
@ConfigurationProperties(prefix = "netopia")
@Getter
@Setter
public class NetopiaProperties {

    /** Whether the online-payment flow is configured/enabled (false → pay-now order points reject). */
    private boolean enabled;

    /** API key sent as the {@code Authorization} header to the gateway. */
    private String apiKey;

    /** POS signature identifying the merchant account. */
    private String posSignature;

    /** Gateway base URL (sandbox vs live). */
    private String baseUrl;

    /** Our own public IPN URL the gateway calls server-to-server on status changes. */
    private String notifyUrl;

    private String language = "ro";
    private String currency = "RON";
    private String emailTemplate = "";

    /** Placeholder billing identity for anonymous self-service customers. */
    private String guestEmail = "guest@yammer.app";
    private String guestPhone = "0700000000";
}
