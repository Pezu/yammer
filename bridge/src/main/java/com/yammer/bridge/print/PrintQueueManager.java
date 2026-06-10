package com.yammer.bridge.print;

import com.yammer.bridge.dto.InfoReceiptRequest;
import com.yammer.bridge.dto.ReceiptRequest;
import com.yammer.bridge.dto.ReceiptResult;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Serializes print jobs onto a single worker thread.
 *
 * <p>Fiscal devices don't tolerate concurrent connections, so every job runs one
 * at a time in submission order. Thermal proforma jobs go through the same queue
 * to keep ordering simple and predictable.
 */
@Slf4j
@Service
public class PrintQueueManager {

    private final FiscalPrinterService fiscalPrinterService;
    private final EscPosThermalService thermalService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "print-queue");
        t.setDaemon(false);
        return t;
    });

    public PrintQueueManager(FiscalPrinterService fiscalPrinterService, EscPosThermalService thermalService) {
        this.fiscalPrinterService = fiscalPrinterService;
        this.thermalService = thermalService;
    }

    /**
     * Print a receipt: fiscally on the cash register when {@code fiscal}, otherwise
     * non-fiscally on the HPRT thermal printer.
     */
    public CompletableFuture<ReceiptResult> submitReceipt(ReceiptRequest request) {
        return CompletableFuture.supplyAsync(
                () -> request.fiscal()
                        ? fiscalPrinterService.print(request)
                        : thermalService.printReceipt(request),
                executor);
    }

    public CompletableFuture<ReceiptResult> submitInfo(InfoReceiptRequest request) {
        return CompletableFuture.supplyAsync(() -> thermalService.print(request), executor);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
