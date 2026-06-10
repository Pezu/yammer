package com.yammer.bridge.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A receipt to print, received from the backend.
 *
 * <p>{@code fiscal} selects the device:
 * <ul>
 *   <li>{@code true}  → fiscal receipt on the DATECS cash register at {@link #cashRegister()}</li>
 *   <li>{@code false} → non-fiscal receipt on the HPRT thermal printer at {@link #printerIp()}</li>
 * </ul>
 *
 * @param requestId     correlation id echoed back in the result
 * @param fiscal        print fiscally on the cash register, or non-fiscally on the thermal printer
 * @param paymentMethod {@code CASH} | {@code CARD} | {@code CHECK}
 * @param cashRegister  LAN IP of the cash register (fiscal)
 * @param printerIp     LAN IP of the HPRT thermal printer (non-fiscal)
 * @param lines         receipt lines (unit price is VAT-inclusive)
 */
public record ReceiptRequest(
        String requestId,
        boolean fiscal,
        String paymentMethod,
        String cashRegister,
        String printerIp,
        List<Line> lines) {

    /**
     * @param name      product/service name
     * @param quantity  quantity sold
     * @param unitPrice VAT-inclusive unit price
     * @param vat       VAT rate in percent (e.g. 21, 11, 0)
     */
    public record Line(String name, double quantity, BigDecimal unitPrice, BigDecimal vat) {
    }
}
