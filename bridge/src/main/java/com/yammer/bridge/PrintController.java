package com.yammer.bridge;

import com.yammer.bridge.dto.InfoReceiptRequest;
import com.yammer.bridge.dto.ReceiptRequest;
import com.yammer.bridge.dto.ReceiptResult;
import com.yammer.bridge.print.PrintQueueManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manual print endpoints for local testing — same serialized queue as backend jobs.
 *
 * <pre>
 * curl -X POST http://localhost:8085/print -H 'Content-Type: application/json' \
 *      -H 'X-Bridge-Key: <key>' -d '{
 *   "requestId":"t1","paymentMethod":"CARD","cashRegister":"192.168.0.188",
 *   "lines":[{"name":"Cola","quantity":2,"unitPrice":12.00,"vat":21}] }'
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/print")
public class PrintController {

    private final PrintQueueManager queue;
    private final BridgeProperties props;

    @PostMapping
    public ResponseEntity<ReceiptResult> printReceipt(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @RequestBody ReceiptRequest request)
            throws ExecutionException, InterruptedException {
        requireKey(key);
        return await(queue.submitReceipt(request));
    }

    @PostMapping("/info")
    public ResponseEntity<ReceiptResult> printInfo(
            @RequestHeader(value = "X-Bridge-Key", required = false) String key,
            @RequestBody InfoReceiptRequest request)
            throws ExecutionException, InterruptedException {
        requireKey(key);
        return await(queue.submitInfo(request));
    }

    /**
     * Shared-secret guard: requires the {@code X-Bridge-Key} header to equal
     * {@code bridge.api-key} when that property is non-blank (mirrors
     * BridgeAuthHandshakeInterceptor semantics). Uses constant-time comparison to
     * prevent timing attacks. When the configured key is blank, the check is skipped
     * (dev convenience).
     */
    private void requireKey(String provided) {
        String configured = props.apiKey();
        if (configured == null || configured.isBlank()) {
            return; // dev mode — no key configured
        }
        if (provided == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Bridge-Key header");
        }
        byte[] a = configured.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Bridge-Key");
        }
    }

    /** Wait for a queued print job, bounded by {@code bridge.print-timeout-seconds}. */
    private ResponseEntity<ReceiptResult> await(CompletableFuture<ReceiptResult> job)
            throws ExecutionException, InterruptedException {
        try {
            return ResponseEntity.ok(job.get(props.printTimeoutSeconds(), TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            job.cancel(true);
            log.warn("Print job timed out after {}s", props.printTimeoutSeconds());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
        }
    }
}
