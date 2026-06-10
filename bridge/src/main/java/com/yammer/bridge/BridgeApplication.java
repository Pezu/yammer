package com.yammer.bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Yammer on-prem bridge.
 *
 * <p>Runs at the venue alongside the fiscal/printing hardware. It connects
 * outbound to the yammer backend over a raw WebSocket, receives print jobs, and
 * drives:
 * <ul>
 *   <li>DATECS DP-25MX fiscal cash registers (TCP :3999) for fiscal receipts,</li>
 *   <li>ESC/POS thermal printers (TCP :9100) for non-fiscal proforma bills.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(BridgeApplication.class, args);
    }
}
