package com.yammer.bridge.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yammer.bridge.BridgeProperties;
import com.yammer.bridge.dto.InfoReceiptRequest;
import com.yammer.bridge.dto.ReceiptRequest;
import com.yammer.bridge.dto.ReceiptResult;
import com.yammer.bridge.print.PrintQueueManager;
import com.yammer.bridge.print.Qty;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Raw WebSocket client to the yammer backend (mirrors yammer's own WS stack —
 * no STOMP/SockJS). Connects outbound, authenticates with {@code ?key=<api-key>}
 * (and an {@code X-Bridge-Key} header), and reconnects automatically.
 *
 * <p>Protocol — newline-free JSON text frames tagged with {@code type}:
 * <ul>
 *   <li>backend → bridge: {@code {"type":"RECEIPT", ...}} / {@code {"type":"INFO_RECEIPT", ...}}</li>
 *   <li>bridge → backend: {@code {"type":"RECEIPT_RESULT", ...}}</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BridgeWebSocketClient extends TextWebSocketHandler {

    private static final String HEADER_API_KEY = "X-Bridge-Key";
    private static final String TYPE_RECEIPT = "RECEIPT";
    private static final String TYPE_INFO = "INFO_RECEIPT";
    private static final String TYPE_RESULT = "RECEIPT_RESULT";

    private final BridgeProperties props;
    private final PrintQueueManager queue;
    private final ObjectMapper mapper;
    private final ProcessedReceiptStore processedStore;
    private final StandardWebSocketClient client = new StandardWebSocketClient();

    /** Receipts handed to the printer but not yet finished — closes the de-dup window during printing. */
    private final java.util.Set<String> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile WebSocketSession session;

    // ─── connection lifecycle ────────────────────────────────────────────────

    /**
     * Keeps the backend connection up. Runs on Spring's scheduler every
     * {@code bridge.reconnect-delay-seconds} (the suffix turns the seconds value
     * into milliseconds), starting immediately after boot. A no-op while connected;
     * otherwise it (re)connects. The bridge is unattended, so it must recover on its
     * own from backend restarts, network blips and idle-timeouts — raw Spring
     * WebSocket has no built-in reconnect.
     */
    @Scheduled(initialDelay = 0, fixedDelayString = "${bridge.reconnect-delay-seconds:10}000")
    public void ensureConnected() {
        WebSocketSession current = this.session;
        if (current != null && current.isOpen()) {
            return;
        }
        if (props.apiKey() == null || props.apiKey().isBlank() || "change-me".equals(props.apiKey())) {
            log.warn("bridge.api-key is not set (BRIDGE_API_KEY). The backend will reject the connection.");
        }
        try {
            URI uri = buildUri();
            log.info("Connecting to backend WebSocket: {}", redact(uri));

            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            if (props.apiKey() != null && !props.apiKey().isBlank()) {
                headers.add(HEADER_API_KEY, props.apiKey());
            }

            this.session = client.execute(this, headers, uri).get(30, TimeUnit.SECONDS);
            log.info("Connected to backend WebSocket.");
            sendHello();
        } catch (Exception ex) {
            this.session = null;
            log.error("WebSocket connect failed: {}. Retrying in {}s...",
                    ex.getMessage(), props.reconnectDelaySeconds());
        }
    }

    private URI buildUri() {
        // The API key is sent via the X-Bridge-Key header (see headers above), not as a query
        // param — a query value would be percent-encoded (e.g. '/' → %2F) and the backend reads
        // the raw param without decoding, which breaks keys containing '/' or '='.
        return URI.create(props.serverUrl());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.session = null;
        log.warn("WebSocket connection closed: {}", status);
    }

    // ─── inbound messages ────────────────────────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            JsonNode node = mapper.readTree(payload);
            String type = node.path("type").asText("");
            switch (type) {
                case TYPE_RECEIPT -> handleReceipt(node);
                case TYPE_INFO -> handleInfo(node);
                default -> log.debug("Ignoring message of type '{}'", type);
            }
        } catch (Exception ex) {
            log.error("Failed to handle inbound message: {}", ex.getMessage(), ex);
        }
    }

    private void handleReceipt(JsonNode node) throws Exception {
        ReceiptRequest request = mapper.treeToValue(node, ReceiptRequest.class);

        // Idempotency: the backend re-sends PENDING receipts on reconnect. If we already
        // printed this requestId, return the cached result instead of printing again.
        Optional<ReceiptResult> cached = processedStore.get(request.requestId());
        if (cached.isPresent()) {
            log.info("Duplicate receipt requestId={} — returning cached result, not re-printing.",
                    request.requestId());
            sendResult(cached.get());
            return;
        }
        // Already printing this requestId (a duplicate frame arrived before the first finished) —
        // drop it so the same receipt isn't printed twice. The first frame still sends the result.
        if (!inFlight.add(request.requestId())) {
            log.info("Receipt requestId={} already in flight — skipping duplicate frame.",
                    request.requestId());
            return;
        }

        String kind = request.fiscal() ? "FISCAL" : "NON-FISCAL";
        String ip = request.fiscal() ? request.cashRegister() : request.printerIp();
        log.info("Received {} receipt: requestId={} device={} method={}",
                kind, request.requestId(), ip, request.paymentMethod());
        if (request.lines() != null) {
            for (ReceiptRequest.Line l : request.lines()) {
                log.info("    {} x {} @ {}{}", Qty.label(l.quantity()), l.name(), l.unitPrice(),
                        l.vat() == null ? "" : " (VAT " + l.vat() + "%)");
            }
        }
        queue.submitReceipt(request)
                .thenAccept(result -> {
                    if (ReceiptResult.OK.equalsIgnoreCase(result.status())) {
                        processedStore.put(request.requestId(), result);
                    }
                    // keep failed ones reservable so a later resend can retry the print
                    inFlight.remove(request.requestId());
                    sendResult(result);
                })
                .exceptionally(ex -> {
                    inFlight.remove(request.requestId());
                    return logAsyncFailure(request.requestId(), ex);
                });
    }

    private void sendHello() {
        WebSocketSession s = this.session;
        if (s == null || !s.isOpen()) {
            return;
        }
        try {
            synchronized (s) {
                s.sendMessage(new TextMessage("{\"type\":\"HELLO\"}"));
            }
            log.info("Sent HELLO to backend (flush pending receipts).");
        } catch (Exception ex) {
            log.warn("Failed to send HELLO: {}", ex.getMessage());
        }
    }

    private void handleInfo(JsonNode node) throws Exception {
        InfoReceiptRequest request = mapper.treeToValue(node, InfoReceiptRequest.class);
        log.info("Received PROFORMA: requestId={} device={} table={} total={}",
                request.requestId(), request.printerIp(), request.table(), request.total());
        if (request.lines() != null) {
            for (InfoReceiptRequest.Line l : request.lines()) {
                log.info("    {} x {} @ {}", l.quantity(), l.name(), l.lineTotal());
            }
        }
        queue.submitInfo(request)
                .thenAccept(this::sendResult)
                .exceptionally(ex -> logAsyncFailure(request.requestId(), ex));
    }

    private Void logAsyncFailure(String requestId, Throwable ex) {
        log.error("Async print failed requestId={}: {}", requestId, ex.getMessage(), ex);
        return null;
    }

    // ─── outbound result ─────────────────────────────────────────────────────

    private void sendResult(ReceiptResult result) {
        WebSocketSession s = this.session;
        if (s == null || !s.isOpen()) {
            log.warn("No live WebSocket session — result NOT sent (requestId={}).", result.requestId());
            return;
        }
        try {
            ObjectNode out = mapper.valueToTree(result);
            out.put("type", TYPE_RESULT);
            synchronized (s) {
                s.sendMessage(new TextMessage(mapper.writeValueAsString(out)));
            }
            log.info("Sent RECEIPT_RESULT requestId={} status={}", result.requestId(), result.status());
        } catch (Exception ex) {
            log.error("Failed to send result requestId={}: {}", result.requestId(), ex.getMessage(), ex);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String redact(URI uri) {
        return uri.toString().replaceAll("key=[^&]*", "key=***");
    }
}
