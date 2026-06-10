package com.yammer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.entity.FiscalStatus;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.MenuItemEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import com.yammer.entity.VatTypeEntity;
import com.yammer.event.PaymentCommittedEvent;
import com.yammer.repository.IntegrationRepository;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.VatTypeRepository;
import com.yammer.ws.BridgeWsHandler;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Backend side of the on-prem bridge: builds print jobs, pushes them over the bridge
 * WebSocket, and applies the fiscal result back to the payment.
 *
 * <ul>
 *   <li><b>Fiscal receipts</b> — on a committed non-protocol payment, an
 *       {@code RECEIPT} (fiscal) frame is sent to the order point's cash register;
 *       the {@code RECEIPT_RESULT} updates {@code payment.fiscal_status} / receipt no.</li>
 *   <li><b>Proforma</b> — {@link #sendInfo} pushes an {@code INFO_RECEIPT} to the
 *       order point's thermal printer (non-fiscal, no write-back).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BridgeService {

    private final BridgeWsHandler handler;
    private final ObjectMapper mapper;
    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final IntegrationRepository integrationRepository;
    private final MenuItemRepository menuItemRepository;
    private final VatTypeRepository vatTypeRepository;

    /** A proforma line (non-fiscal). */
    public record InfoLine(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    // ─── proforma (non-fiscal) ───────────────────────────────────────────────

    /** Push the non-fiscal proforma to the order point's thermal printer. */
    public void sendInfo(OrderPointEntity op, List<Integer> orderNos, List<InfoLine> lines, BigDecimal total) {
        String printerIp = deviceIp(op.getPrinterId());
        if (printerIp == null) {
            log.warn("No thermal printer (with IP) configured for order point '{}' — proforma not sent.",
                    op.getName());
            return;
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "INFO_RECEIPT");
        msg.put("requestId", UUID.randomUUID().toString());
        msg.put("printerIp", printerIp);
        msg.put("table", op.getName());
        msg.put("orderNos", orderNos);
        msg.put("lines", lines.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", l.name());
            m.put("quantity", l.quantity());
            m.put("unitPrice", l.unitPrice());
            m.put("lineTotal", l.lineTotal());
            return m;
        }).toList());
        msg.put("total", total);
        sendJson(msg);
    }

    // ─── fiscal receipt (on committed payment) ───────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCommitted(PaymentCommittedEvent event) {
        try {
            PaymentEntity payment = paymentRepository.findById(event.paymentId()).orElse(null);
            if (payment == null
                    || payment.getMethod() == PaymentMethod.PROTOCOL
                    || payment.getFiscalStatus() == FiscalStatus.SUCCESS) {
                return;
            }
            OrderPointEntity op = orderPointRepository.findById(payment.getOrderPointId()).orElse(null);
            if (op == null) {
                return;
            }
            String cashRegisterIp = deviceIp(op.getCashRegisterId());
            if (cashRegisterIp == null) {
                log.warn("No cash register (with IP) for order point '{}' — payment {} left PENDING.",
                        op.getName(), payment.getId());
                return;
            }
            List<OrderItemEntity> items = orderItemRepository.findByPaymentId(payment.getId());
            if (items.isEmpty()) {
                return;
            }

            Map<UUID, BigDecimal> vatByMenuItem = resolveVat(items);

            List<Map<String, Object>> lines = new ArrayList<>(items.size());
            for (OrderItemEntity it : items) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", it.getName());
                m.put("quantity", it.getQuantity());
                m.put("unitPrice", it.getPrice() == null ? BigDecimal.ZERO : it.getPrice());
                m.put("vat", it.getMenuItemId() == null ? null : vatByMenuItem.get(it.getMenuItemId()));
                lines.add(m);
            }

            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", "RECEIPT");
            msg.put("requestId", payment.getId().toString());
            msg.put("fiscal", true);
            msg.put("paymentMethod", payment.getMethod().name());
            msg.put("cashRegister", cashRegisterIp);
            msg.put("lines", lines);
            sendJson(msg);
            log.info("Sent fiscal RECEIPT for payment {} to {}", payment.getId(), cashRegisterIp);
        } catch (Exception e) {
            // fiscalization must never break the payment flow
            log.error("Failed to dispatch fiscal receipt for payment {}: {}",
                    event.paymentId(), e.getMessage(), e);
        }
    }

    // ─── result write-back ───────────────────────────────────────────────────

    /** Apply a {@code RECEIPT_RESULT} (fiscal receipts only; proforma results are ignored). */
    @Transactional
    public void onResult(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String requestId = node.path("requestId").asText(null);
            if (requestId == null) {
                return;
            }
            UUID paymentId;
            try {
                paymentId = UUID.fromString(requestId);
            } catch (IllegalArgumentException notAPayment) {
                return; // e.g. a proforma result
            }
            PaymentEntity payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment == null) {
                return;
            }
            boolean ok = "OK".equalsIgnoreCase(node.path("status").asText());
            payment.setFiscalStatus(ok ? FiscalStatus.SUCCESS : FiscalStatus.FAILED);
            String receiptNumber = node.path("receiptNumber").asText(null);
            if (receiptNumber != null && !receiptNumber.isBlank()) {
                payment.setReceiptNumber(receiptNumber);
            }
            paymentRepository.save(payment);
            log.info("Fiscal result for payment {}: {}", paymentId, payment.getFiscalStatus());
        } catch (Exception e) {
            log.error("Failed to apply fiscal result: {}", e.getMessage(), e);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Resolve an integration id to its IP, or null if missing/blank. */
    private String deviceIp(UUID integrationId) {
        if (integrationId == null) {
            return null;
        }
        IntegrationEntity device = integrationRepository.findById(integrationId).orElse(null);
        if (device == null || device.getIp() == null || device.getIp().isBlank()) {
            return null;
        }
        return device.getIp();
    }

    /** menuItemId → VAT percentage, for the items' products. */
    private Map<UUID, BigDecimal> resolveVat(List<OrderItemEntity> items) {
        Set<UUID> menuItemIds = items.stream()
                .map(OrderItemEntity::getMenuItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (menuItemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> vatTypeByMenuItem = new HashMap<>();
        for (MenuItemEntity mi : menuItemRepository.findAllById(menuItemIds)) {
            if (mi.getVatTypeId() != null) {
                vatTypeByMenuItem.put(mi.getId(), mi.getVatTypeId());
            }
        }
        Map<UUID, BigDecimal> valueByVatType = vatTypeRepository
                .findAllById(new HashSet<>(vatTypeByMenuItem.values())).stream()
                .collect(Collectors.toMap(VatTypeEntity::getId, VatTypeEntity::getValue));
        Map<UUID, BigDecimal> result = new HashMap<>();
        vatTypeByMenuItem.forEach((menuItemId, vatTypeId) -> {
            BigDecimal value = valueByVatType.get(vatTypeId);
            if (value != null) {
                result.put(menuItemId, value);
            }
        });
        return result;
    }

    private void sendJson(Map<String, Object> message) {
        try {
            handler.send(mapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("Failed to serialize bridge message: {}", e.getMessage(), e);
        }
    }
}
