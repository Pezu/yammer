package com.yammer.bridge;

import com.yammer.bridge.dto.InfoReceiptRequest;
import com.yammer.bridge.dto.ReceiptRequest;
import com.yammer.bridge.dto.ReceiptResult;
import com.yammer.bridge.print.PrintQueueManager;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
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

    public PrintController(PrintQueueManager queue) {
        this.queue = queue;
    }

    @PostMapping
    public ResponseEntity<ReceiptResult> printReceipt(@RequestBody ReceiptRequest request)
            throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(queue.submitReceipt(request).get());
    }

    @PostMapping("/info")
    public ResponseEntity<ReceiptResult> printInfo(@RequestBody InfoReceiptRequest request)
            throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(queue.submitInfo(request).get());
    }
}
