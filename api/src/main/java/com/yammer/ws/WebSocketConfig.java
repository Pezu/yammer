package com.yammer.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWsHandler orderWsHandler;
    private final WsAuthHandshakeInterceptor authInterceptor;
    private final BridgeWsHandler bridgeWsHandler;
    private final BridgeAuthHandshakeInterceptor bridgeAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWsHandler, "/ws/orders")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
        registry.addHandler(bridgeWsHandler, "/ws/bridge")
                .addInterceptors(bridgeAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
