package com.yammer.bridge.print;

import com.yammer.bridge.dto.ReceiptRequest;
import com.yammer.bridge.fiscal.DatecsDPMXProtocol;
import com.yammer.bridge.fiscal.DatecsErrorMapper;
import com.yammer.bridge.dto.ReceiptResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Prints a fiscal receipt on a DATECS DP-25MX.
 *
 * <p>Per request: open a TCP connection to the cash register, cancel any leftover
 * open receipt (recovery), open a fiscal receipt with a unique sale number, add
 * the lines under the matching VAT group, total with the payment method, close
 * the receipt, and return the document number.
 */
@Slf4j
@Service
public class FiscalPrinterService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    /** Cap the TCP connect so an unreachable register can't wedge the single print-queue worker. */
    private static final int CONNECT_TIMEOUT_MS = 8_000;

    private final PrinterProperties props;

    /** Unique-Number-of-Sale counter per cash-register IP. */
    private final ConcurrentHashMap<String, AtomicLong> unsSeqPerIp = new ConcurrentHashMap<>();

    /**
     * VAT% → DATECS fiscal group. Must match how the device's tax groups are
     * programmed: 1=A (21%), 2=B (11%), 3=C (0%). Unknown rates fall back to group A.
     */
    private static final Map<Integer, Integer> VAT_TO_TAX_GROUP = Map.of(21, 1, 11, 2, 0, 3);

    /** Payment method → DATECS cmd 53 payMode. */
    private static final Map<String, Integer> PAYMENT_TO_PAYMODE = Map.of(
            "CASH", DatecsDPMXProtocol.PAY_CASH,
            "CARD", DatecsDPMXProtocol.PAY_CARD,
            "CHECK", DatecsDPMXProtocol.PAY_CHECK,
            "CEC", DatecsDPMXProtocol.PAY_CHECK);

    public FiscalPrinterService(PrinterProperties props) {
        this.props = props;
    }

    public ReceiptResult print(ReceiptRequest request) {
        log.info("Fiscal print: requestId={} cashRegister={} method={} lines={}",
                request.requestId(), request.cashRegister(), request.paymentMethod(),
                request.lines() == null ? 0 : request.lines().size());

        String host = request.cashRegister();
        if (host == null || host.isBlank()) {
            log.error("Fiscal print requestId={} has no cashRegister IP", request.requestId());
            return ReceiptResult.builder()
                    .status(ReceiptResult.ERROR)
                    .requestId(request.requestId())
                    .paymentMethod(request.paymentMethod())
                    .issuedAt(LocalDateTime.now())
                    .errorCode("NO_DEVICE")
                    .errorMessage("Missing cash register IP")
                    .build();
        }
        int port = props.tcp().port();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            DatecsDPMXProtocol fp = new DatecsDPMXProtocol(socket);
            return doPrint(fp, request, host);
        } catch (Exception ex) {
            log.error("Fiscal print failed requestId={}: {}", request.requestId(), ex.getMessage(), ex);
            DatecsErrorMapper.MappedError err = DatecsErrorMapper.map(ex, host);
            return ReceiptResult.builder()
                    .status(ReceiptResult.ERROR)
                    .requestId(request.requestId())
                    .paymentMethod(request.paymentMethod())
                    .issuedAt(LocalDateTime.now())
                    .errorCode(err.code)
                    .errorMessage(err.message)
                    .build();
        }
    }

    private ReceiptResult doPrint(DatecsDPMXProtocol fp, ReceiptRequest request, String host)
            throws Exception {
        String opCode = props.operator().code();
        String opPass = props.operator().pass();
        String tillNum = props.tillNumber();

        // 1. Recovery — cancel a previously left-open receipt (cmd 60).
        fp.cancelFiscalCheck();

        // 2. Open the fiscal receipt with a per-register unique sale number (cmd 48).
        String uns = generateUns(host);
        long allReceipt = fp.openFiscalCheck(opCode, opPass, tillNum, uns);
        syncUnsCounter(host, allReceipt);

        // 3. Lines (cmd 49).
        if (request.lines() != null) {
            for (ReceiptRequest.Line line : request.lines()) {
                int taxGroup = resolveVatGroup(line.vat());
                double price = (line.unitPrice() == null ? BigDecimal.ZERO : line.unitPrice()).doubleValue();
                fp.sell(line.name(), taxGroup, price, line.quantity());
            }
        }

        // 4. Fiscal footer text (cmd 54).
        fp.printFiscalText("Multumim pentru vizita!");

        // 5. Total with the payment method (cmd 53).
        int payMode = resolvePayMode(request.paymentMethod());
        fp.total(payMode);

        // 6. Close the receipt and read the document number (cmd 56).
        DatecsDPMXProtocol.ReceiptInfo info = fp.closeFiscalCheck();
        String docNumber = info.docNumber == null || info.docNumber.isEmpty() ? null : info.docNumber;

        ReceiptResult result = ReceiptResult.builder()
                .status(ReceiptResult.OK)
                .requestId(request.requestId())
                .receiptNumber(docNumber)
                .fiscalReceiptId(docNumber)
                .cashRegisterSerial(props.serialNumber().isEmpty() ? host : props.serialNumber())
                .issuedAt(LocalDateTime.now())
                .totalAmount(calculateTotal(request))
                .paymentMethod(request.paymentMethod())
                .build();

        log.info("Fiscal print OK: requestId={} receiptNumber={} total={}",
                result.requestId(), result.receiptNumber(), result.totalAmount());
        return result;
    }

    private String generateUns(String ip) {
        long seq = unsSeqPerIp.computeIfAbsent(ip, k -> new AtomicLong(0L)).incrementAndGet();
        return String.format("%s-%s-%07d", LocalDate.now().format(DATE_FMT), formatTill(props.tillNumber()), seq);
    }

    private void syncUnsCounter(String ip, long allReceipt) {
        if (allReceipt <= 0L) {
            return;
        }
        AtomicLong counter = unsSeqPerIp.computeIfAbsent(ip, k -> new AtomicLong(0L));
        long prev = counter.getAndSet(allReceipt);
        if (prev != allReceipt) {
            log.info("UNS sync [{}]: {} -> {} (AllReceipt from device)", ip, prev, allReceipt);
        }
    }

    private String formatTill(String till) {
        try {
            return String.format("%04d", Long.parseLong(till.trim()));
        } catch (NumberFormatException e) {
            return (till + "0000").substring(0, 4);
        }
    }

    private int resolveVatGroup(BigDecimal vat) {
        if (vat == null) {
            return 1;
        }
        return VAT_TO_TAX_GROUP.getOrDefault(vat.setScale(0, RoundingMode.HALF_UP).intValue(), 1);
    }

    private int resolvePayMode(String paymentMethod) {
        if (paymentMethod == null) {
            return DatecsDPMXProtocol.PAY_CASH;
        }
        return PAYMENT_TO_PAYMODE.getOrDefault(paymentMethod.toUpperCase(), DatecsDPMXProtocol.PAY_CASH);
    }

    private BigDecimal calculateTotal(ReceiptRequest request) {
        if (request.lines() == null) {
            return BigDecimal.ZERO;
        }
        return request.lines().stream()
                .map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
