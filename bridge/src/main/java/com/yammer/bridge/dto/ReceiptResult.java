package com.yammer.bridge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * Result of a print job, sent back to the backend.
 *
 * @param status      {@code OK} | {@code ERROR}
 * @param requestId   correlation id from the originating request
 * @param receiptNumber       cash-register document number (fiscal only)
 * @param fiscalReceiptId     fiscal document id (fiscal only)
 * @param cashRegisterSerial  device serial (or IP when serial is unknown)
 * @param issuedAt    when the receipt was issued
 * @param totalAmount VAT-inclusive total
 * @param paymentMethod payment method echoed from the request
 * @param errorCode   short error code when {@code status == ERROR}
 * @param errorMessage human-readable error when {@code status == ERROR}
 */
@Builder
public record ReceiptResult(
        String status,
        String requestId,
        String receiptNumber,
        String fiscalReceiptId,
        String cashRegisterSerial,
        LocalDateTime issuedAt,
        BigDecimal totalAmount,
        String paymentMethod,
        String errorCode,
        String errorMessage) {

    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    /**
     * Convenience factory for error results. Pass {@code null} for fields that are
     * not known at the error site (e.g. {@code paymentMethod} when the request never
     * reached that stage).
     */
    public static ReceiptResult error(
            String requestId, String paymentMethod, String code, String message) {
        return ReceiptResult.builder()
                .status(ERROR)
                .requestId(requestId)
                .paymentMethod(paymentMethod)
                .issuedAt(LocalDateTime.now())
                .errorCode(code)
                .errorMessage(message)
                .build();
    }
}
