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
import com.yammer.event.BridgeReadyEvent;
import com.yammer.event.PaymentCommittedEvent;
import com.yammer.repository.IntegrationRepository;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.VatTypeRepository;
import com.yammer.ws.BridgeWsHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Backend side of the on-prem bridge, built as a durable, at-least-once outbox so the
 * fiscal flow survives the bridge disconnecting/reconnecting (e.g. Cloud Run's request
 * timeout, deploys, network blips).
 *
 * <p>The {@code payment} row <em>is</em> the outbox:
 * {@code PENDING} (needs fiscalizing) → {@code SUCCESS}/{@code FAILED} (terminal, set
 * from {@code RECEIPT_RESULT}). A {@code RECEIPT} frame is only ever pushed while a
 * bridge session is live; anything still {@code PENDING} is re-sent when the bridge
 * (re)connects and periodically thereafter. Delivery is idempotent — {@code requestId}
 * is the stable payment id and the bridge de-dupes — so a re-send never double-prints.
 *
 * <p>Proforma ({@code INFO_RECEIPT}) is non-fiscal and best-effort: it is only sent when
 * a bridge is connected and is not queued (the waiter can re-press it).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BridgeService {

    /** A PENDING payment sent more than this many seconds ago is presumed lost and re-sent. */
    private static final long STALE_RESEND_SECONDS = 90;

    private final BridgeWsHandler handler;
    private final ObjectMapper mapper;
    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final IntegrationRepository integrationRepository;
    private final MenuItemRepository menuItemRepository;
    private final VatTypeRepository vatTypeRepository;

    /** Serializes dispatch runs (scheduler / event / commit) on this single instance. */
    private final ReentrantLock dispatchLock = new ReentrantLock();

    /** A proforma line (non-fiscal). */
    public record InfoLine(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    // ─── proforma (non-fiscal, best-effort) ──────────────────────────────────

    /** Push the non-fiscal proforma to the order point's thermal printer (only if a bridge is live). */
    public void sendInfo(OrderPointEntity op, List<Integer> orderNos, List<InfoLine> lines, BigDecimal total) {
        if (!handler.isConnected()) {
            log.warn("No bridge connected — proforma for '{}' not sent (re-press when online).", op.getName());
            return;
        }
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
        try {
            handler.send(mapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("Failed to serialize proforma: {}", e.getMessage(), e);
        }
    }

    // ─── fiscal dispatch (durable outbox) ────────────────────────────────────

    /** A committed (non-protocol) payment becomes PENDING; flush immediately if a bridge is live. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCommitted(PaymentCommittedEvent event) {
        dispatchPending();
    }

    /** Safety net: re-attempt PENDING/stale receipts on a fixed cadence. */
    @Scheduled(fixedDelayString = "${bridge.dispatch-interval-ms:10000}")
    @Transactional
    public void dispatchScheduled() {
        dispatchPending();
    }

    /** On bridge (re)connect / HELLO, flush everything that accumulated while it was offline. */
    @Async
    @Transactional
    @EventListener
    public void onBridgeReady(BridgeReadyEvent event) {
        dispatchPending();
    }

    /**
     * Sends every dispatchable PENDING payment — but only while a bridge session is live,
     * so nothing goes out during a reconnect. Each send stamps {@code fiscalSentAt} so a
     * lost ack is retried after {@link #STALE_RESEND_SECONDS} (the bridge de-dupes the
     * replay). Must be invoked within a transaction (callers are transactional).
     */
    private void dispatchPending() {
        if (!handler.isConnected()) {
            return;
        }
        if (!dispatchLock.tryLock()) {
            return; // another dispatch is already running
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(STALE_RESEND_SECONDS);
            List<PaymentEntity> due = paymentRepository.findFiscalDispatchable(FiscalStatus.PENDING, cutoff);
            if (due.isEmpty()) {
                return;
            }

            // Batch-load everything the frames need (was ~5 queries + 1 save per payment).
            Set<UUID> opIds = due.stream()
                    .map(PaymentEntity::getOrderPointId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, OrderPointEntity> opsById = orderPointRepository.findAllById(opIds).stream()
                    .collect(Collectors.toMap(OrderPointEntity::getId, Function.identity()));
            Set<UUID> cashRegisterIds = opsById.values().stream()
                    .map(OrderPointEntity::getCashRegisterId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, String> ipByIntegration = integrationRepository.findAllById(cashRegisterIds).stream()
                    .filter(d -> d.getIp() != null && !d.getIp().isBlank())
                    .collect(Collectors.toMap(IntegrationEntity::getId, IntegrationEntity::getIp));
            Map<UUID, List<OrderItemEntity>> itemsByPayment = orderItemRepository
                    .findByPaymentIdIn(due.stream().map(PaymentEntity::getId).toList()).stream()
                    .collect(Collectors.groupingBy(OrderItemEntity::getPaymentId));
            Map<UUID, BigDecimal> vatByMenuItem = resolveVat(
                    itemsByPayment.values().stream().flatMap(List::stream).toList());

            LocalDateTime sentAt = LocalDateTime.now();
            for (PaymentEntity payment : due) {
                try {
                    String frame = buildReceiptFrame(payment, opsById, ipByIntegration, itemsByPayment, vatByMenuItem);
                    if (frame != null) {
                        handler.send(frame);
                        log.info("Dispatched fiscal RECEIPT for payment {}", payment.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to dispatch RECEIPT for payment {}: {}", payment.getId(), e.getMessage(), e);
                }
                // back off either way so a payment with no device doesn't spin every tick
                payment.setFiscalSentAt(sentAt);
            }
            paymentRepository.saveAll(due);
        } finally {
            dispatchLock.unlock();
        }
    }

    /** Build the {@code RECEIPT} JSON frame for a payment, or {@code null} if it can't be fiscalized. */
    private String buildReceiptFrame(
            PaymentEntity payment,
            Map<UUID, OrderPointEntity> opsById,
            Map<UUID, String> ipByIntegration,
            Map<UUID, List<OrderItemEntity>> itemsByPayment,
            Map<UUID, BigDecimal> vatByMenuItem)
            throws Exception {
        if (payment.getMethod() == PaymentMethod.PROTOCOL || payment.getFiscalStatus() == FiscalStatus.SUCCESS) {
            return null;
        }
        OrderPointEntity op = opsById.get(payment.getOrderPointId());
        if (op == null) {
            return null;
        }
        String cashRegisterIp = op.getCashRegisterId() == null ? null : ipByIntegration.get(op.getCashRegisterId());
        if (cashRegisterIp == null) {
            log.warn("No cash register (with IP) for order point '{}' — payment {} left PENDING.",
                    op.getName(), payment.getId());
            return null;
        }
        List<OrderItemEntity> items = itemsByPayment.getOrDefault(payment.getId(), List.of());
        if (items.isEmpty()) {
            return null;
        }

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
        return mapper.writeValueAsString(msg);
    }

    // ─── result write-back (the ack) ─────────────────────────────────────────

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
}
