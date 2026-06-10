package com.yammer.bridge.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.bridge.dto.ReceiptResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Idempotency guard for fiscal receipts. The backend re-sends PENDING {@code RECEIPT}s
 * on reconnect (at-least-once), so the bridge must never print the same {@code requestId}
 * twice — a known id returns its cached {@code RECEIPT_RESULT} instead of re-printing.
 *
 * <p>Backed by an append-only JSONL file so dedup survives a bridge restart (double-printing
 * a fiscal receipt is a legal problem, not just a UX one). Entries older than {@link #TTL}
 * are dropped on load.
 */
@Component
@Slf4j
public class ProcessedReceiptStore {

    private static final Duration TTL = Duration.ofDays(7);

    private final ObjectMapper mapper;
    private final Path file;
    private final Map<String, ReceiptResult> processed = new ConcurrentHashMap<>();

    public ProcessedReceiptStore(ObjectMapper mapper,
                                 @Value("${bridge.processed-store:processed-receipts.jsonl}") String path) {
        this.mapper = mapper;
        this.file = Path.of(path);
        load();
    }

    /** The cached result for a previously-printed receipt, if any. */
    public Optional<ReceiptResult> get(String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(processed.get(requestId));
    }

    /** Record a completed receipt (idempotent key = {@code requestId}) and persist it. */
    public synchronized void put(String requestId, ReceiptResult result) {
        if (requestId == null || result == null) {
            return;
        }
        processed.put(requestId, result);
        try {
            String line = mapper.writeValueAsString(new Entry(Instant.now().toString(), requestId, result));
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Could not persist processed receipt {}: {}", requestId, e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Instant cutoff = Instant.now().minus(TTL);
            int loaded = 0;
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    Entry e = mapper.readValue(line, Entry.class);
                    if (Instant.parse(e.at()).isAfter(cutoff)) {
                        processed.put(e.requestId(), e.result());
                        loaded++;
                    }
                } catch (Exception bad) {
                    log.debug("Skipping unparseable processed-receipt line: {}", bad.getMessage());
                }
            }
            log.info("Loaded {} processed receipt(s) for idempotency from {}", loaded, file);
        } catch (IOException e) {
            log.warn("Could not load processed-receipt store {}: {}", file, e.getMessage());
        }
    }

    private record Entry(String at, String requestId, ReceiptResult result) {
    }
}
