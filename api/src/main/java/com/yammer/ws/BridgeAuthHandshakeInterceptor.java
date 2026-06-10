package com.yammer.ws;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the on-prem bridge's WebSocket handshake with a shared API key,
 * supplied either as a {@code ?key=<key>} query param or an {@code X-Bridge-Key}
 * header. The bridge is a machine (not a user), so it uses an API key rather than
 * the JWT used by {@link WsAuthHandshakeInterceptor}.
 *
 * <p>If {@code bridge.api-key} is blank the handshake is accepted (dev convenience).
 */
@Component
@Slf4j
public class BridgeAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final String HEADER_API_KEY = "X-Bridge-Key";

    @Value("${bridge.api-key:}")
    private String apiKey;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("bridge.api-key is blank — accepting bridge handshake without authentication (dev only).");
            return true;
        }
        String provided = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("key");
        if (provided == null) {
            provided = request.getHeaders().getFirst(HEADER_API_KEY);
        }
        if (apiKey.equals(provided)) {
            return true;
        }
        log.warn("Rejected bridge handshake: invalid or missing API key.");
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
