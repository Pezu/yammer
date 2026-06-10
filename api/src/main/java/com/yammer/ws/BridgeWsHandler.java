package com.yammer.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.service.BridgeService;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Holds the live on-prem bridge WebSocket session(s) and is the channel the backend
 * uses to push print jobs ({@code RECEIPT} / {@code INFO_RECEIPT}) and receive their
 * outcome ({@code RECEIPT_RESULT}). Usually a single bridge connects per backend.
 */
@Component
@Slf4j
public class BridgeWsHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper;
    private final BridgeService bridgeService;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public BridgeWsHandler(ObjectMapper mapper, @Lazy BridgeService bridgeService) {
        this.mapper = mapper;
        this.bridgeService = bridgeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Bridge connected ({} live session(s)).", sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.warn("Bridge disconnected: {} ({} live session(s)).", status, sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            if ("RECEIPT_RESULT".equals(node.path("type").asText())) {
                bridgeService.onResult(message.getPayload());
            }
        } catch (Exception e) {
            log.error("Failed to handle bridge message: {}", e.getMessage(), e);
        }
    }

    /** Whether at least one bridge session is open. */
    public boolean isConnected() {
        return sessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    /** Push a JSON frame to every live bridge session. */
    public void send(String json) {
        TextMessage msg = new TextMessage(json);
        boolean sent = false;
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) {
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
                sent = true;
            } catch (IOException e) {
                log.warn("Failed to push frame to bridge: {}", e.getMessage());
            }
        }
        if (!sent) {
            log.warn("No live bridge session — frame dropped.");
        }
    }
}
