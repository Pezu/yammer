package com.yammer.bridge;

import com.yammer.bridge.dto.InfoReceiptRequest;
import com.yammer.bridge.dto.ReceiptRequest;
import com.yammer.bridge.dto.ReceiptResult;
import com.yammer.bridge.print.PrintQueueManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual print endpoints for local testing — same serialized queue as backend jobs.
 *
 * <pre>
 * curl -X POST http://localhost:8085/print -H 'Content-Type: application/json' -d '{
 *   "requestId":"t1","paymentMethod":"CARD","cashRegister":"192.168.0.188",
 *   "lines":[{"name":"Cola","quantity":2,"unitPrice":12.00,"vat":21}] }'
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/print")
public class PrintController {

    private final PrintQueueManager queue;
    private final BridgeProperties props;

    public PrintController(PrintQueueManager queue, BridgeProperties props) {
        this.queue = queue;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<ReceiptResult> printReceipt(@RequestBody ReceiptRequest request)
            throws ExecutionException, InterruptedException {
        return await(queue.submitReceipt(request));
    }

    @PostMapping("/info")
    public ResponseEntity<ReceiptResult> printInfo(@RequestBody InfoReceiptRequest request)
            throws ExecutionException, InterruptedException {
        return await(queue.submitInfo(request));
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
