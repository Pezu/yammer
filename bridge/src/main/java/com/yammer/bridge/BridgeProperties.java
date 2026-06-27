package com.yammer.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backend connection settings (prefix {@code bridge}).
 *
 * @param serverUrl             raw WebSocket URL of the backend bridge endpoint
 *                              (e.g. {@code ws://host:8080/ws/bridge})
 * @param apiKey                  shared secret sent as the {@code ?key=} query param
 * @param reconnectDelaySeconds   delay between reconnection attempts
 * @param printTimeoutSeconds     per-receipt timeout (queue wait + actual print)
 * @param maxConnectionAgeMinutes proactively recycle the connection after this many minutes so the
 *                                bridge re-routes to the current Cloud Run revision after a deploy
 *                                (a long-lived WS otherwise pins to the old revision's instance)
 */
@ConfigurationProperties(prefix = "bridge")
public record BridgeProperties(
        String serverUrl,
        String apiKey,
        long reconnectDelaySeconds,
        long printTimeoutSeconds,
        long maxConnectionAgeMinutes) {

    public BridgeProperties {
        if (reconnectDelaySeconds <= 0) {
            reconnectDelaySeconds = 10;
        }
        if (printTimeoutSeconds <= 0) {
            printTimeoutSeconds = 30;
        }
        if (maxConnectionAgeMinutes <= 0) {
            maxConnectionAgeMinutes = 5;
        }
    }
}
