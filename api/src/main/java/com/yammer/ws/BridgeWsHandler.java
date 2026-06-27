package com.yammer.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.event.BridgeReadyEvent;
import com.yammer.service.BridgeService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 *
 * <p>On (re)connect and on a {@code HELLO} frame it publishes a {@link BridgeReadyEvent}
 * so {@link BridgeService} flushes any PENDING fiscal receipts. On shutdown it closes
 * sessions with {@code GOING_AWAY} so the bridge reconnects immediately rather than
 * waiting for a TCP timeout (matters for Cloud Run's ~10s SIGTERM grace).
 */
@Component
@Slf4j
public class BridgeWsHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper;
    private final BridgeService bridgeService;
    private final ApplicationEventPublisher events;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public BridgeWsHandler(ObjectMapper mapper, @Lazy BridgeService bridgeService, ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.bridgeService = bridgeService;
        this.events = events;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Single active bridge: a new connection supersedes any previous one. Closing the older
        // (possibly half-open) session means we never broadcast the same fiscal frame to two
        // sessions — which would print the receipt twice.
        for (WebSocketSession old : sessions) {
            if (old != session) {
                closeQuietly(old);
            }
        }
        sessions.clear();
        sessions.add(session);
        log.info("Bridge connected (superseding any previous session).");
        events.publishEvent(new BridgeReadyEvent());
    }

    private void closeQuietly(WebSocketSession s) {
        try {
            if (s.isOpen()) {
                s.close(CloseStatus.GOING_AWAY);
            }
        } catch (Exception e) {
            log.debug("Error closing superseded bridge session: {}", e.getMessage());
        }
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
            String type = node.path("type").asText("");
            switch (type) {
                case "RECEIPT_RESULT" -> bridgeService.onResult(message.getPayload());
                case "HELLO" -> {
                    log.info("Bridge HELLO received — flushing pending receipts.");
                    events.publishEvent(new BridgeReadyEvent());
                }
                default -> log.debug("Ignoring bridge message of type '{}'", type);
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

    /** Close sessions cleanly on shutdown so the bridge reconnects fast (no TCP wait). */
    @PreDestroy
    public void closeSessions() {
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) {
                    s.close(CloseStatus.GOING_AWAY);
                }
            } catch (IOException e) {
                log.debug("Error closing bridge session on shutdown: {}", e.getMessage());
            }
        }
        sessions.clear();
    }
}
