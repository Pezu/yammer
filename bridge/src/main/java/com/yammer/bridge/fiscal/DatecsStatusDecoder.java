package com.yammer.bridge.fiscal;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodeaza cei 8 bytes de status pe care DATECS DP-25MX (protocol "X") ii
 * intoarce in fiecare raspuns:
 *
 *   01 LEN SEQ CMD DATA 04 [STATUS8] 05 BCC 03
 *
 * Definitiile bitilor sunt preluate din specificatia protocolului X
 * (echivalent {@code org.eda.protocol.DeviceDatecsXV1} din FPGate), traduse in
 * romana. Fiecare bit setat reprezinta o stare a casei de marcat. Unele stari
 * sunt erori care blocheaza emiterea bonului (lipsa hartie, memorie fiscala
 * plina, capac deschis), altele sunt avertismente sau pur informative.
 *
 * Scopul clasei este sa transforme starea bruta intr-un mesaj lizibil care
 * poate fi logat — pana acum acesti bytes erau cititi si aruncati, deci
 * cauza reala a unui refuz al casei nu aparea nicaieri in log.
 */
public final class DatecsStatusDecoder {

    public enum Severity { ERROR, WARNING, INFO }

    /** Un bit de status: byte-ul (0..7), pozitia bitului (0..7), gravitatea, mesajul. */
    private record Bit(int byteIndex, int bit, Severity severity, String message) {}

    // Definitii conform protocolului X (S0..S5). Bitii marcati "#" in spec sunt
    // erori; "*" sunt erori de memorie fiscala; restul avertismente/informativi.
    private static final Bit[] BITS = {
            // S0 — uz general
            new Bit(0, 0, Severity.ERROR,   "Eroare de sintaxa in comanda"),
            new Bit(0, 1, Severity.ERROR,   "Cod de comanda invalid"),
            new Bit(0, 2, Severity.WARNING, "Data/ora nesincronizate"),
            new Bit(0, 4, Severity.ERROR,   "Eroare in mecanismul de tiparire"),
            new Bit(0, 5, Severity.ERROR,   "Eroare generala a casei de marcat"),
            new Bit(0, 6, Severity.ERROR,   "Capacul imprimantei este deschis"),
            // S1 — uz general
            new Bit(1, 0, Severity.ERROR,   "Depasire (overflow) la executarea comenzii"),
            new Bit(1, 1, Severity.ERROR,   "Comanda nepermisa in acest context"),
            // S2 — hartie / jurnal electronic / bon
            new Bit(2, 0, Severity.ERROR,   "Sfarsit de hartie"),
            new Bit(2, 1, Severity.WARNING, "Aproape sfarsit de hartie"),
            new Bit(2, 2, Severity.ERROR,   "Jurnalul electronic (KLEN) este plin"),
            new Bit(2, 3, Severity.INFO,    "Bon fiscal deschis"),
            new Bit(2, 4, Severity.WARNING, "Jurnalul electronic (KLEN) aproape plin"),
            new Bit(2, 5, Severity.INFO,    "Bon de serviciu (nefiscal) deschis"),
            // S4 — memorie fiscala
            new Bit(4, 0, Severity.ERROR,   "Eroare la accesul memoriei fiscale"),
            new Bit(4, 3, Severity.WARNING, "Spatiu pentru mai putin de 60 de inchideri fiscale"),
            new Bit(4, 4, Severity.ERROR,   "Memoria fiscala este plina"),
            new Bit(4, 5, Severity.ERROR,   "Eroare la memoria fiscala"),
            new Bit(4, 6, Severity.ERROR,   "Memoria fiscala lipseste sau este deteriorata"),
            // S5 — memorie fiscala (informativ)
            new Bit(5, 3, Severity.INFO,    "Aparatul este in regim fiscal"),
    };

    private DatecsStatusDecoder() {}

    /** Rezultatul decodarii: liste separate de erori, avertismente si informatii. */
    public static final class Decoded {
        public final List<String> errors   = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<String> info     = new ArrayList<>();

        public boolean hasErrors()   { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }

        /** Erori + avertismente intr-un singur string, pentru mesaje de eroare. */
        public String summary() {
            List<String> all = new ArrayList<>(errors);
            all.addAll(warnings);
            return String.join("; ", all);
        }
    }

    /** Decodeaza cei (pana la) 8 bytes de status in mesaje lizibile. */
    public static Decoded decode(byte[] status) {
        Decoded d = new Decoded();
        if (status == null) return d;
        for (Bit def : BITS) {
            if (def.byteIndex() >= status.length) continue;
            int b = status[def.byteIndex()] & 0xFF;
            if ((b & (1 << def.bit())) == 0) continue;
            switch (def.severity()) {
                case ERROR   -> d.errors.add(def.message());
                case WARNING -> d.warnings.add(def.message());
                case INFO    -> d.info.add(def.message());
            }
        }
        return d;
    }

    /** Reprezentare hex a bytes-ilor de status, pentru debug: "S0=A1 S1=80 ...". */
    public static String toHex(byte[] status) {
        if (status == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < status.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("S%d=%02X", i, status[i] & 0xFF));
        }
        return sb.toString();
    }
}
