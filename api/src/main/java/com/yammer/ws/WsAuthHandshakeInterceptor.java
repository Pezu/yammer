package com.yammer.ws;

import com.yammer.service.JwtService;
import io.jsonwebtoken.Claims;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the WebSocket handshake from a {@code ?token=<jwt>} query parameter
 * (browsers can't set Authorization headers on a WebSocket) and stores the username
 * on the session attributes.
 */
@Component
@RequiredArgsConstructor
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = jwtService.parse(token);
            attributes.put(OrderWsHandler.USERNAME_ATTR, claims.getSubject());
            return true;
        } catch (Exception e) {
            return false; // invalid/expired token → reject the handshake
        }
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
