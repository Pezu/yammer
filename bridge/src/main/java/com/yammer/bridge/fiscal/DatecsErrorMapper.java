package com.yammer.bridge.fiscal;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mapeaza erorile brute DATECS si exceptiile de retea la coduri si mesaje
 * lizibile pentru ReceiptResponse.
 */
public final class DatecsErrorMapper {

    private DatecsErrorMapper() {}

    /** Rezultatul maparii: errorCode + errorMessage */
    public static class MappedError {
        public final String code;
        public final String message;

        MappedError(String code, String message) {
            this.code    = code;
            this.message = message;
        }
    }

    // Regex ca sa extraga codul numeric din mesaje de tip:
    //   "Eroare cmd 48: [-111024]"  →  -111024
    private static final Pattern DATECS_CODE_PATTERN =
            Pattern.compile("\\[(-?\\d+)\\]");

    public static MappedError map(Exception ex, String host) {
        String rawMsg = ex.getMessage() != null ? ex.getMessage() : "";

        // ── Erori de retea ─────────────────────────────────────────────────────
        if (ex instanceof ConnectException || ex instanceof NoRouteToHostException) {
            return new MappedError(
                    "DATECS_UNREACHABLE",
                    "Nu se poate conecta la casa de marcat [" + host + "]. "
                    + "Verificati ca imprimanta este pornita si conectata in retea.");
        }
        if (ex instanceof SocketTimeoutException) {
            return new MappedError(
                    "DATECS_TIMEOUT",
                    "Timeout la comunicarea cu casa de marcat [" + host + "]. "
                    + "Imprimanta nu a raspuns in timp util.");
        }
        if (ex instanceof UnknownHostException) {
            return new MappedError(
                    "DATECS_UNKNOWN_HOST",
                    "Adresa IP/hostname invalida pentru casa de marcat: " + host);
        }

        // ── Erori DATECS (cod numeric in mesaj) ────────────────────────────────
        Matcher m = DATECS_CODE_PATTERN.matcher(rawMsg);
        if (m.find()) {
            int datecsCode = Integer.parseInt(m.group(1));
            return mapDatecsCode(datecsCode, rawMsg);
        }

        // ── Altceva (eroare de protocol, EOF, etc.) ────────────────────────────
        return new MappedError("COMM_ERROR", rawMsg);
    }

    private static MappedError mapDatecsCode(int code, String rawMsg) {
        switch (code) {

            // ── Parametru invalid (cmd 48 cu UNS neacceptat de imprimanta) ────────
            // -112001: imprimanta a respins parametrul transmis la deschiderea bonului.
            // Cauze posibile: UNS cu format gresit, ordine campuri incorecta,
            // sau imprimanta nu suporta UNS in aceasta varianta de protocol.
            case -112001:
                return new MappedError(
                        "DATECS_INVALID_PARAMETER",
                        "Imprimanta a respins un parametru la deschiderea bonului (cod: -112001). "
                        + "Verificati formatul UNS sau ordinea campurilor din cmd 48.");

            // ── Raport Z lipsa ──────────────────────────────────────────────────
            // Imprimanta refuza sa deschida un bon nou pana cand nu se inchide
            // ziua fiscala precedenta (raport Z zilnic obligatoriu ANAF).
            case -111024:
                return new MappedError(
                        "DATECS_Z_REPORT_REQUIRED",
                        "Casa de marcat necesita raport Z pentru ziua/zilele fiscale "
                        + "precedente inainte de a putea emite un bon nou. "
                        + "Printati raportul Z de pe tastatura casei de marcat "
                        + "si reincercati.");

            // ── Nu exista bon deschis ───────────────────────────────────────────
            // Cmd 60 (cancel) returnat cand nu e niciun bon in curs — normal la
            // recovery, dar poate aparea si la cmd 53/56 daca bonul s-a pierdut.
            case -111016:
                return new MappedError(
                        "DATECS_NO_OPEN_RECEIPT",
                        "Nu exista bon fiscal deschis pe casa de marcat. "
                        + "Operatiunea de anulare/inchidere bon nu poate continua.");

            // ── Bon deja deschis ────────────────────────────────────────────────
            // Cmd 48 refuzat pentru ca exista deja un bon in curs.
            case -111025:
                return new MappedError(
                        "DATECS_RECEIPT_ALREADY_OPEN",
                        "Exista deja un bon fiscal deschis pe casa de marcat. "
                        + "Inchideti sau anulati bonul curent si reincercati.");

            // ── Eroare operator / parola ────────────────────────────────────────
            case -111008:
                return new MappedError(
                        "DATECS_OPERATOR_ERROR",
                        "Cod operator sau parola incorecta configurata in printer-app-fpgate. "
                        + "Verificati proprietatile printer.operator.code si printer.operator.pass.");

            // ── Suma articol invalida ───────────────────────────────────────────
            case -111040:
                return new MappedError(
                        "DATECS_INVALID_AMOUNT",
                        "Suma sau cantitatea unui articol este invalida (zero sau negativa). "
                        + "Verificati datele din PrintReceiptRequest.");

            // ── Grup fiscal invalid ─────────────────────────────────────────────
            case -111033:
                return new MappedError(
                        "DATECS_INVALID_TAX_GROUP",
                        "Grup TVA invalid transmis casei de marcat. "
                        + "Valori acceptate: 1(A/21%), 2(B/11%), 3(C/0%).");

            // ── Memorie fiscala plina ───────────────────────────────────────────
            case -111060:
            case -111061:
                return new MappedError(
                        "DATECS_FISCAL_MEMORY_FULL",
                        "Memoria fiscala a casei de marcat este plina. "
                        + "Contactati service-ul autorizat DATECS.");

            // ── Imprimanta offline / hartie lipsa ───────────────────────────────
            case -111070:
                return new MappedError(
                        "DATECS_PRINTER_ERROR",
                        "Eroare imprimanta: lipsa hartie sau capac deschis. "
                        + "Verificati hartia termica si inchideti capacul imprimantei.");

            // ── Eroare generica de stare fiscala (-111xxx) ──────────────────────
            default:
                if (code <= -111000 && code > -112000) {
                    return new MappedError(
                            "DATECS_FISCAL_STATE_ERROR",
                            "Eroare de stare fiscala DATECS (cod: " + code + "). "
                            + "Verificati starea casei de marcat si consultati "
                            + "manualul DATECS DP-25MX.");
                }
                // Orice altceva
                return new MappedError("COMM_ERROR", rawMsg);
        }
    }
}
