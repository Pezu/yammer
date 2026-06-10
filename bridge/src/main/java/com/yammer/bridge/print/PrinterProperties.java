package com.yammer.bridge.print;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cash-register (DATECS DP-25MX) settings (prefix {@code printer}).
 *
 * <p>The per-request {@code cashRegister} IP overrides {@link Tcp#host()} at runtime.
 */
@ConfigurationProperties(prefix = "printer")
public record PrinterProperties(
        Tcp tcp,
        Operator operator,
        String tillNumber,
        String serialNumber) {

    public PrinterProperties {
        if (tcp == null) {
            tcp = new Tcp(3999);
        }
        if (operator == null) {
            operator = new Operator("1", "0001");
        }
        if (tillNumber == null || tillNumber.isBlank()) {
            tillNumber = "1";
        }
        if (serialNumber == null) {
            serialNumber = "";
        }
    }

    /** Fixed TCP port of the cash register (the IP arrives per request). */
    public record Tcp(int port) {
        public Tcp {
            if (port <= 0) {
                port = 3999;
            }
        }
    }

    /** Operator credentials used to open the fiscal receipt. */
    public record Operator(String code, String pass) {
        public Operator {
            if (code == null || code.isBlank()) {
                code = "1";
            }
            if (pass == null || pass.isBlank()) {
                pass = "0001";
            }
        }
    }
}
