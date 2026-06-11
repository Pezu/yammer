package com.yammer.ws;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Tracks live service-app WebSocket sessions per username and pushes order events
 * only to the users they concern (so each service sees only its own orders).
 */
@Component
@Slf4j
public class OrderWsHandler extends TextWebSocketHandler {

    static final String USERNAME_ATTR = "username";

    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = username(session);
        if (username == null) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (IOException ignored) {
                // nothing to do
            }
            return;
        }
        sessionsByUser.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = username(session);
        if (username == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByUser.get(username);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(username);
            }
        }
    }

    /** Send a text payload to every live session of the given users. */
    public void sendToUsers(Collection<String> usernames, String payload) {
        TextMessage message = new TextMessage(payload);
        for (String username : usernames) {
            Set<WebSocketSession> sessions = sessionsByUser.get(username);
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        // WebSocketSession is not safe for concurrent sends; serialize per session
                        // (a scheduler tick and an order event can push at the same time).
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                    }
                } catch (IOException e) {
                    log.debug("Failed to push WS message to {}: {}", username, e.getMessage());
                }
            }
        }
    }

    private String username(WebSocketSession session) {
        Object value = session.getAttributes().get(USERNAME_ATTR);
        return value == null ? null : value.toString();
    }
}
