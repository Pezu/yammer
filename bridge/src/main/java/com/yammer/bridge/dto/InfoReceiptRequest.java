package com.yammer.bridge.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A non-fiscal "proforma" bill to print on an ESC/POS thermal printer.
 *
 * @param requestId correlation id echoed back in the result
 * @param printerIp LAN IP of the target thermal printer
 * @param table     table / order-point label printed as the header
 * @param orderNos  order numbers included on the bill
 * @param lines     bill lines
 * @param total     grand total
 */
public record InfoReceiptRequest(
        String requestId,
        String printerIp,
        String table,
        List<Integer> orderNos,
        List<Line> lines,
        BigDecimal total) {

    public record Line(String name, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
