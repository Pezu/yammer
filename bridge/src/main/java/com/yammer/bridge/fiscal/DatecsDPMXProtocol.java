package com.yammer.bridge.fiscal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * Protocol TCP al Datecs DP-25MX (varianta cu 4-nibble LEN/CMD).
 *
 * SEND:    01 [LEN4] [SEQ1] [CMD4] [DATA] 05 [BCC4] 03
 * RECEIVE: 01 [LEN4] [SEQ1] [CMD4] [DATA] 04 [STATUS8] 05 [BCC4] 03
 *
 * LEN4  = 4 nibble-uri (fiecare + 0x30) ale valorii (0x2A + len(DATA))
 * CMD4  = 4 nibble-uri ale numarului de comanda
 * BCC   = suma bytes de la LEN4[0] pana la postamble 0x05 (exclusiv preamble)
 * STATUS = 8 bytes
 */
public class DatecsDPMXProtocol {

    private static final Logger log = LoggerFactory.getLogger(DatecsDPMXProtocol.class);

    private static final int STATUS_BYTES    = 8;
    private static final int MAX_RETRIES     = 3;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final Charset CP1252      = Charset.forName("windows-1252");

    // Coduri de plata (cmd 53 payMode)
    public static final int PAY_CASH    = 0;
    public static final int PAY_CARD    = 1;
    public static final int PAY_CHECK   = 2;

    private final Socket       socket;
    private final InputStream  in;
    private final OutputStream out;
    private int seq = 0x20;  // SEQ incepe de la 0x20

    /** Acumuleaza octetii raspunsului curent pentru logarea cadrului complet (RX). */
    private final java.io.ByteArrayOutputStream rxFrame = new java.io.ByteArrayOutputStream();

    /** Cei 8 bytes de status din ultimul raspuns primit (vezi {@link DatecsStatusDecoder}). */
    private byte[] lastStatus = new byte[STATUS_BYTES];

    /** Informatii despre bonul inchis (populate dupa closeFiscalCheck) */
    public static class ReceiptInfo {
        public String docNumber   = "";   // numarul bonului
        public String rawResponse = "";   // raspunsul brut pentru debugging
    }

    public DatecsDPMXProtocol(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(READ_TIMEOUT_MS);
        this.in  = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    // ─── API fiscal ────────────────────────────────────────────────────────────

    /**
     * Cmd 48 — deschide bon fiscal.
     *
     * <p>Parametrul {@code uns} (Unique Number of Sale) este acceptat in semnatura dar
     * NU este transmis dispozitivului — imprimanta returna eroare (-112001) la sintaxa
     * cu UNS; se foloseste sintaxa fara UNS pana la clarificarea ordinii campurilor cu
     * DATECS Romania.
     *
     * @param opCode   codul operatorului
     * @param opPass   parola operatorului
     * @param till     numarul casei (till)
     * @param uns      rezervat pentru utilizare ulterioara (momentan ignorat in transmisie)
     * @return AllReceipt — numarul total de bonuri emise de la ultimul raport Z,
     *         incluzand bonul tocmai deschis; folosit pentru sincronizarea contorului UNS.
     */
    public long openFiscalCheck(String opCode, String opPass, String till, String uns) throws IOException {
        // TODO: UNS (Unique Number of Sale) — testat, imprimanta returneaza -112001.
        // Probabil ordinea campurilor difera in protocolul tab-based al DP-25MX
        // fata de varianta comma documentata. Necesita clarificare cu DATECS Romania.
        // Deocamdata folosim sintaxa fara UNS: <OpCode>\t<OpPwd>\t<TillNmb>\t
        // uns parametru pastrat in semnatura pentru utilizare ulterioara.
        String data = opCode + "\t" + opPass + "\t" + till + "\t";
        String resp = send(48, data);
        checkOk(48, resp);

        // Parseaza AllReceipt din raspuns: "0\t<AllReceipt>\t<FiscReceipt>\t..."
        long allReceipt = 0L;
        String[] parts = resp.split("\t");
        if (parts.length >= 2) {
            try { allReceipt = Long.parseLong(parts[1].trim()); } catch (NumberFormatException ignored) {}
        }
        log.info("Bon fiscal deschis (UNS={}, AllReceipt={}). Raspuns brut: [{}]", uns, allReceipt, resp.trim());
        return allReceipt;
    }

    /**
     * Cmd 49 — adauga articol.
     *
     * @param name     denumirea produsului
     * @param taxGroup grupul fiscal DATECS: 1=A(21%), 2=B(11%), 3=C(0%)
     * @param price    pretul unitar
     * @param qty      cantitatea
     */
    public void sell(String name, int taxGroup, double price, double qty) throws IOException {
        String data = name + "\t" + taxGroup + "\t"
                + String.format("%.2f", price) + "\t"
                + String.format("%.3f", qty) + "\t\t\t1\tBUC.\t";
        String resp = send(49, data);
        checkOk(49, resp);
        log.info("Articol adaugat: '{}' taxGrp={} pret={} qty={}", name, taxGroup,
                String.format("%.2f", price), String.format("%.3f", qty));
    }

    /** Cmd 54 — tipareste text fiscal (ex: nota de multumire) */
    public void printFiscalText(String text) throws IOException {
        String data = text + "\t0\t0\t0\t0\t1\t";
        String resp = send(54, data);
        checkOk(54, resp);
        log.debug("Text fiscal tiparit: {}", text);
    }

    /**
     * Cmd 60 — anuleaza bonul fiscal deschis (daca exista).
     * Nu arunca exceptie daca nu exista un bon deschis — folosit pentru recovery.
     */
    public void cancelFiscalCheck() {
        try {
            String resp = send(60, "");
            log.info("cancelFiscalCheck raspuns: [{}]", resp.trim());
        } catch (Exception ex) {
            log.warn("cancelFiscalCheck ignorat (probabil nu era bon deschis): {}", ex.getMessage());
        }
    }

    /**
     * Cmd 53 — inregistreaza plata si totalizeaza bonul.
     *
     * @param payMode  0=numerar (CASH), 1=card (CARD), 2=CEC (CHECK)
     * @param amount   suma platita (0 = suma exacta, casa calculeaza singura)
     * @return raspunsul brut de la imprimanta (tab-delimitat, primul camp = cod eroare)
     */
    public String total(int payMode, double amount) throws IOException {
        // Format: "<payMode>\t[<amount>]\t"
        String amountStr = (amount > 0) ? String.format("%.2f", amount) : "";
        String data = payMode + "\t" + amountStr + "\t";
        String resp = send(53, data);
        checkOk(53, resp);
        log.info("Plata inregistrata payMode={} amount={}. Raspuns brut: [{}]",
                payMode, amount > 0 ? String.format("%.2f", amount) : "auto", resp.trim());
        return resp;
    }

    /** Suprascriere pentru suma exacta (0 = auto) */
    public String total(int payMode) throws IOException {
        return total(payMode, 0);
    }

    /**
     * Cmd 56 — inchide bonul fiscal.
     *
     * @return ReceiptInfo cu numarul bonului extras din raspuns
     */
    public ReceiptInfo closeFiscalCheck() throws IOException {
        log.info("Inchid bonul (tiparire in progres, poate dura ~2s)...");
        String resp = send(56, "");
        checkOk(56, resp);
        log.info("Bon inchis. Raspuns brut: [{}]", resp.trim());

        ReceiptInfo info = new ReceiptInfo();
        info.rawResponse = resp.trim();
        info.docNumber   = parseDocNumber(resp);
        return info;
    }

    /**
     * Incearca sa extraga numarul documentului din raspunsul brut.
     * Format tipic DATECS dupa cmd 56 sau 53:
     *   "0\t<docNum>\t<alteCampuri>\t"
     *   sau simplu "0\t"
     */
    private String parseDocNumber(String resp) {
        if (resp == null || resp.isEmpty()) return "";
        String[] parts = resp.split("\t");
        // parts[0] = cod eroare ("0" = OK), parts[1] = docNum (daca exista)
        if (parts.length >= 2 && !parts[1].isBlank()) {
            return parts[1].trim();
        }
        return "";
    }

    // ─── Transport ─────────────────────────────────────────────────────────────

    /**
     * Trimite comanda si returneaza DATA din raspuns.
     * Retry automat la NACK.
     */
    public String send(int cmd, String data) throws IOException {
        byte[] dataBytes = data.getBytes(CP1252);
        int dataLen = dataBytes.length;

        // LEN = 0x2A + len(DATA)
        int lenVal = 0x2A + dataLen;

        // Packet: 01 LEN4 SEQ CMD4 DATA 05 BCC4 03
        int pktSize = 1 + 4 + 1 + 4 + dataLen + 1 + 4 + 1;
        byte[] pkt  = new byte[pktSize];
        int off = 0;

        pkt[off++] = 0x01;                        // preamble
        encNibbles(pkt, off, lenVal); off += 4;   // LEN4
        pkt[off++] = (byte) seq;                   // SEQ
        encNibbles(pkt, off, cmd);   off += 4;    // CMD4
        System.arraycopy(dataBytes, 0, pkt, off, dataLen); off += dataLen;
        pkt[off++] = 0x05;                         // postamble

        // BCC: suma de la LEN4[0] (index 1 in pkt) pana la 0x05 inclusiv
        int crc = 0;
        for (int i = 1; i < off; i++) crc += (pkt[i] & 0xFF);
        encNibbles(pkt, off, crc); off += 4;      // BCC4
        pkt[off++] = 0x03;                         // terminator

        log.info("→ CR cmd={} seq=0x{} [{}B]: {}",
                cmd, Integer.toHexString(seq), off, toHex(pkt, off));

        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            out.write(pkt, 0, off);
            out.flush();

            // Asteapta pana primim ceva altceva decat SYN (0x16) sau null
            int b;
            int waitBytes = 0;
            do {
                b = in.read();
                if (b < 0) throw new IOException("Conexiunea inchisa de imprimanta");
                if (b == 0x16 || b == 0x00) waitBytes++;
            } while (b == 0x16 || b == 0x00);
            if (waitBytes > 0) {
                log.info("← CR cmd={} busy: {} octeti SYN/NUL inainte de raspuns", cmd, waitBytes);
            }

            if (b == 0x15) {
                log.warn("← CR cmd={} NACK (0x15), retry {}/{}", cmd, retry + 1, MAX_RETRIES);
                continue;
            }
            if (b == 0x01) {
                rxFrame.reset();
                rxFrame.write(0x01); // preambulul deja consumat — il includem in cadru
                String result = receivePacket(cmd);
                byte[] frame = rxFrame.toByteArray();
                log.info("← CR cmd={} [{}B]: {}", cmd, frame.length, toHex(frame, frame.length));
                log.info("← CR cmd={} data=[{}]", cmd, result.trim());
                return result;
            }
            throw new IOException("Byte neasteptat de la imprimanta: 0x" + Integer.toHexString(b));
        }
        throw new IOException("Cmd " + cmd + ": max retry-uri atinse, fara raspuns valid");
    }

    /** Parseaza restul unui pachet raspuns (preamblul 0x01 a fost deja consumat). */
    private String receivePacket(int expectedCmd) throws IOException {
        byte[] len4 = readFully(4);
        int lenVal  = decNibbles(len4);
        // dataLen = lenVal - 0x33
        int dataLen = lenVal - 0x33;
        if (dataLen < 0) throw new IOException("LEN raspuns invalid: " + lenVal);

        int seqByte = readByte();
        if (seqByte != seq)
            log.warn("SEQ mismatch: asteptat 0x{} primit 0x{}",
                    Integer.toHexString(seq), Integer.toHexString(seqByte));

        byte[] cmd4 = readFully(4);
        int respCmd = decNibbles(cmd4);
        if (respCmd != expectedCmd)
            log.warn("CMD mismatch: asteptat {} primit {}", expectedCmd, respCmd);

        byte[] dataBytes = readFully(dataLen);

        int sep = readByte();
        if (sep != 0x04) throw new IOException("Separator asteptat 0x04, primit 0x" + Integer.toHexString(sep));

        // Cei 8 bytes de status — codeaza starea reala a casei (hartie, memorie
        // fiscala, bon deschis, capac etc.).
        lastStatus = readFully(STATUS_BYTES);

        int post = readByte();
        if (post != 0x05) throw new IOException("Postamble asteptat 0x05, primit 0x" + Integer.toHexString(post));

        readFully(4); // BCC — nu verificam

        int term = readByte();
        if (term != 0x03) throw new IOException("Terminator asteptat 0x03, primit 0x" + Integer.toHexString(term));

        // Avansam SEQ
        seq++;
        if (seq > 0x7F) seq = 0x20;

        // Logheaza starea decodata: erorile/avertismentele raportate de casa
        // sunt acum vizibile in log chiar daca raspunsul comenzii e "OK".
        DatecsStatusDecoder.Decoded st = DatecsStatusDecoder.decode(lastStatus);
        if (st.hasErrors()) {
            log.error("Status casa de marcat (cmd {}): ERORI=[{}] | {}",
                    expectedCmd, st.summary(), DatecsStatusDecoder.toHex(lastStatus));
        } else if (st.hasWarnings()) {
            log.warn("Status casa de marcat (cmd {}): avertismente=[{}]",
                    expectedCmd, String.join("; ", st.warnings));
        } else {
            log.debug("Status casa de marcat (cmd {}): [{}]",
                    expectedCmd, DatecsStatusDecoder.toHex(lastStatus));
        }

        return new String(dataBytes, CP1252);
    }

    // ─── Utilitare ─────────────────────────────────────────────────────────────

    private void checkOk(int cmd, String resp) throws IOException {
        if (resp.isEmpty() || resp.charAt(0) != '0') {
            // Adauga starea decodata a casei (daca exista erori hardware/fiscale)
            // la mesajul de eroare, ca sa apara cauza reala in log si in raspuns.
            DatecsStatusDecoder.Decoded st = DatecsStatusDecoder.decode(lastStatus);
            String statusInfo = st.hasErrors() ? " | stare casa: " + st.summary() : "";
            throw new IOException("Eroare cmd " + cmd + ": [" + resp.trim() + "]" + statusInfo);
        }
    }

    private void encNibbles(byte[] buf, int off, int val) {
        buf[off]   = (byte) (((val >> 12) & 0xF) + 0x30);
        buf[off+1] = (byte) (((val >>  8) & 0xF) + 0x30);
        buf[off+2] = (byte) (((val >>  4) & 0xF) + 0x30);
        buf[off+3] = (byte) (((val >>  0) & 0xF) + 0x30);
    }

    private int decNibbles(byte[] b4) {
        return ((b4[0] - 0x30) << 12) | ((b4[1] - 0x30) << 8)
             | ((b4[2] - 0x30) << 4)  |  (b4[3] - 0x30);
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b < 0) throw new IOException("EOF la citire byte");
        rxFrame.write(b);
        return b;
    }

    private byte[] readFully(int len) throws IOException {
        byte[] buf = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(buf, read, len - read);
            if (n < 0) throw new IOException("EOF la citire " + len + " bytes");
            read += n;
        }
        rxFrame.write(buf, 0, read);
        return buf;
    }

    /** Octeti -> sir hex (ex: "01 30 30 ..") pentru logarea cadrelor pe fir. */
    private static String toHex(byte[] buf, int len) {
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", buf[i] & 0xFF));
        }
        return sb.toString();
    }

}
