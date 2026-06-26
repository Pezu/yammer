package com.yammer.bridge.print;

import com.yammer.bridge.dto.InfoReceiptRequest;
import com.yammer.bridge.dto.ReceiptRequest;
import com.yammer.bridge.dto.ReceiptResult;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Prints a non-fiscal "proforma" bill on an ESC/POS thermal printer over raw TCP
 * to {@code ip}:9100. Plain ASCII (Romanian diacritics transliterated) so output
 * is correct regardless of the printer's code page. 80mm paper (~48 chars/line).
 */
@Slf4j
@Service
public class EscPosThermalService {

    private static final int PORT = 9100;
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int LINE_WIDTH = 48;

    // ── ESC/POS control sequences ──
    private static final byte[] INIT = {0x1B, 0x40};
    private static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};
    private static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};
    private static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    private static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};
    private static final byte[] DOUBLE_ON = {0x1D, 0x21, 0x11};
    private static final byte[] DOUBLE_OFF = {0x1D, 0x21, 0x00};
    // Print + feed 6 lines so the printed content clears the head before the cut,
    // otherwise it stays inside the printer and only the blank leader comes out.
    private static final byte[] FEED_LINES = {0x1B, 0x64, 0x06}; // ESC d 6
    private static final byte[] FEED_AND_CUT = {0x1D, 0x56, 0x42, 0x03};

    public ReceiptResult print(InfoReceiptRequest payload) {
        String host = payload.printerIp();
        log.info("Info print: requestId={} printer={}:{} table={}",
                payload.requestId(), host, PORT, payload.table());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();

            out.write(INIT);

            out.write(ALIGN_CENTER);

            // ── company header ──
            InfoReceiptRequest.Company company = payload.company();
            if (company != null) {
                out.write(BOLD_ON);
                out.write(DOUBLE_ON);
                writeLine(out, safe(company.name()));
                out.write(DOUBLE_OFF);
                out.write(BOLD_OFF);
                if (company.cui() != null && !company.cui().isBlank()) {
                    writeLine(out, "CUI " + company.cui());
                }
                if (company.regCom() != null && !company.regCom().isBlank()) {
                    writeLine(out, "Reg. Com. " + company.regCom());
                }
                if (company.address() != null && !company.address().isBlank()) {
                    writeLine(out, company.address());
                }
                if (company.phone() != null && !company.phone().isBlank()) {
                    writeLine(out, "Tel: " + company.phone());
                }
                writeLine(out, sep());
            }

            out.write(BOLD_ON);
            writeLine(out, "PROFORMA");
            out.write(BOLD_OFF);
            writeLine(out, "NU ESTE BON FISCAL");
            if (payload.table() != null && !payload.table().isBlank()) {
                writeLine(out, "Punct: " + payload.table());
            }
            if (payload.waiter() != null && !payload.waiter().isBlank()) {
                writeLine(out, "Ospatar: " + payload.waiter());
            }
            writeLine(out, "Comanda");

            out.write(ALIGN_LEFT);
            writeLine(out, sep());
            if (payload.lines() != null) {
                for (InfoReceiptRequest.Line line : payload.lines()) {
                    int qty = line.quantity() != null ? line.quantity() : 0;
                    writeLine(out, twoCols(qty + "x " + plain(line.name()), money(line.lineTotal())));
                }
            }
            writeLine(out, sep());

            out.write(BOLD_ON);
            writeLine(out, twoCols("TOTAL", money(payload.total())));
            out.write(BOLD_OFF);

            // ── tip (bacsis) options — the customer ticks one by pen ──
            BigDecimal total = payload.total();
            if (total != null && total.signum() > 0) {
                writeLine(out, "");
                writeLine(out, "Bacsis:");
                for (int pct : new int[] {10, 12, 15}) {
                    BigDecimal tip = total.multiply(BigDecimal.valueOf(pct))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    writeLine(out, twoCols("[ ] " + pct + "%", money(tip)));
                }
                // free-form amount the customer writes in by hand
                String sumaLabel = "[ ] Suma: ";
                writeLine(out, sumaLabel + "_".repeat(Math.max(0, LINE_WIDTH - sumaLabel.length())));
            }

            out.write(ALIGN_CENTER);
            writeLine(out, "");
            writeLine(out, "Va multumim!");

            out.write(FEED_LINES);
            out.write(FEED_AND_CUT);
            out.flush();
            settle(socket); // let the printer drain before the socket closes
            log.info("Info print OK: requestId={} printer={}", payload.requestId(), host);
            return ReceiptResult.builder()
                    .status(ReceiptResult.OK)
                    .requestId(payload.requestId())
                    .totalAmount(payload.total())
                    .issuedAt(LocalDateTime.now())
                    .build();
        } catch (Exception ex) {
            log.error("Info print failed requestId={} printer={}: {}",
                    payload.requestId(), host, ex.getMessage(), ex);
            return ReceiptResult.error(payload.requestId(), null, "PRINT_ERROR", ex.getMessage());
        }
    }

    /**
     * Prints a non-fiscal receipt of the sale on the HPRT thermal printer (the
     * fallback when a job is marked non-fiscal). Same layout as the fiscal receipt
     * content, clearly stamped as not a fiscal document.
     */
    public ReceiptResult printReceipt(ReceiptRequest payload) {
        String host = payload.printerIp();
        log.info("Non-fiscal receipt: requestId={} printer={}:{} method={}",
                payload.requestId(), host, PORT, payload.paymentMethod());

        BigDecimal total = BigDecimal.ZERO;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();

            out.write(INIT);

            out.write(ALIGN_CENTER);
            out.write(BOLD_ON);
            writeLine(out, "BON");
            out.write(BOLD_OFF);
            writeLine(out, "NU ESTE BON FISCAL");

            out.write(ALIGN_LEFT);
            writeLine(out, sep());
            if (payload.lines() != null) {
                for (ReceiptRequest.Line line : payload.lines()) {
                    double qty = line.quantity();
                    BigDecimal unit = line.unitPrice() == null ? BigDecimal.ZERO : line.unitPrice();
                    BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                    total = total.add(lineTotal);
                    writeLine(out, twoCols(Qty.label(qty) + "x " + plain(line.name()), money(lineTotal)));
                }
            }
            writeLine(out, sep());

            out.write(BOLD_ON);
            writeLine(out, twoCols("TOTAL", money(total)));
            out.write(BOLD_OFF);
            if (payload.paymentMethod() != null) {
                writeLine(out, twoCols("Plata", payload.paymentMethod()));
            }

            out.write(ALIGN_CENTER);
            writeLine(out, "");
            writeLine(out, "Va multumim!");

            out.write(FEED_LINES);
            out.write(FEED_AND_CUT);
            out.flush();
            settle(socket); // let the printer drain before the socket closes
            log.info("Non-fiscal receipt OK: requestId={} printer={} total={}",
                    payload.requestId(), host, total);
            return ReceiptResult.builder()
                    .status(ReceiptResult.OK)
                    .requestId(payload.requestId())
                    .totalAmount(total)
                    .paymentMethod(payload.paymentMethod())
                    .issuedAt(LocalDateTime.now())
                    .build();
        } catch (Exception ex) {
            log.error("Non-fiscal receipt failed requestId={} printer={}: {}",
                    payload.requestId(), host, ex.getMessage(), ex);
            return ReceiptResult.error(payload.requestId(), payload.paymentMethod(),
                    "PRINT_ERROR", ex.getMessage());
        }
    }

    /**
     * Some thermal printers discard unprinted data on an abrupt disconnect (and just
     * feed/cut blank paper). Half-close the write side to signal "done sending", then
     * pause briefly so the printer drains its buffer and prints before the socket closes.
     */
    private void settle(Socket socket) {
        try {
            socket.shutdownOutput();
        } catch (IOException ignored) {
            // best effort
        }
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeLine(OutputStream out, String text) throws IOException {
        out.write(transliterate(text).getBytes(StandardCharsets.US_ASCII));
        out.write(0x0A);
    }

    /** Left text + right text padded to LINE_WIDTH (right-aligns the amount). */
    private String twoCols(String left, String right) {
        left = transliterate(left);
        right = transliterate(right);
        int pad = LINE_WIDTH - right.length();
        if (left.length() > pad - 1) {
            left = left.substring(0, Math.max(0, pad - 1));
        }
        int spaces = Math.max(1, LINE_WIDTH - left.length() - right.length());
        return left + " ".repeat(spaces) + right;
    }

    private String sep() {
        return "-".repeat(LINE_WIDTH);
    }


    private String money(BigDecimal v) {
        return String.format("%.2f RON", v == null ? BigDecimal.ZERO : v);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Product names are stored as rich text (HTML). Strip the markup — and the small-font
     * description block — down to the product title on a single plain line for the receipt.
     */
    private String plain(String html) {
        if (html == null) {
            return "";
        }
        String s = html
                .replaceAll("(?is)<font[^>]*size=[\"']?1[\"']?[^>]*>.*?</font>", "")
                .replaceAll("(?is)<small\\b[^>]*>.*?</small>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
        for (String line : s.split("\n")) {
            String t = line.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty()) {
                return t;
            }
        }
        return "";
    }

    /** Map Romanian diacritics to ASCII; replace any other non-ASCII with '?'. */
    private String transliterate(String s) {
        if (s == null) {
            return "";
        }
        return s
                .replace('ă', 'a').replace('Ă', 'A')
                .replace('â', 'a').replace('Â', 'A')
                .replace('î', 'i').replace('Î', 'I')
                .replace('ș', 's').replace('Ș', 'S').replace('ş', 's').replace('Ş', 'S')
                .replace('ț', 't').replace('Ț', 'T').replace('ţ', 't').replace('Ţ', 'T')
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
