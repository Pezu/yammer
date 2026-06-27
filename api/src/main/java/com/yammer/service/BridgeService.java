package com.yammer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.entity.FiscalStatus;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.MenuItemEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.config.CompanyProperties;
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
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backend side of the on-prem bridge. Fiscal receipts are sent <strong>best-effort, once</strong>:
 * a payment is fiscalized when it commits (or when the user manually re-issues it), only while a
 * bridge is live. There is intentionally <strong>no automatic retry</strong> — an auto-resend could
 * overlap a manual re-issue and print the same fiscal receipt twice, so retrying is left entirely to
 * the user from the UI. The {@code payment} row still tracks status ({@code PENDING} →
 * {@code SUCCESS}/{@code FAILED} from {@code RECEIPT_RESULT}) so the UI knows what to re-issue, and
 * the bridge de-dupes by the stable payment-id {@code requestId} as a safety net.
 *
 * <p>Proforma ({@code INFO_RECEIPT}) is non-fiscal and best-effort: only sent when a bridge is
 * connected, not queued (the waiter can re-press it).
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
    private final PlatformTransactionManager transactionManager;
    private final CompanyProperties companyProperties;

    /** Runs the short load+stamp+persist step in its own tx so WS sends happen AFTER commit. */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTx() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** A proforma line (non-fiscal). */
    public record InfoLine(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    // ─── proforma (non-fiscal, best-effort) ──────────────────────────────────

    /** Push the non-fiscal proforma to the order point's thermal printer (only if a bridge is live). */
    public void sendInfo(
            OrderPointEntity op, String waiter, List<Integer> orderNos, List<InfoLine> lines, BigDecimal total) {
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
        Map<String, Object> company = new LinkedHashMap<>();
        company.put("name", companyProperties.getName());
        company.put("cui", companyProperties.getCui());
        company.put("regCom", companyProperties.getRegCom());
        company.put("address", companyProperties.getAddress());
        company.put("phone", companyProperties.getPhone());

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "INFO_RECEIPT");
        msg.put("requestId", UUID.randomUUID().toString());
        msg.put("printerIp", printerIp);
        msg.put("table", op.getName());
        msg.put("waiter", waiter);
        msg.put("company", company);
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

    /**
     * Fiscalize a just-committed (or manually-retried) payment — best-effort, send-once, NO retry.
     * If no bridge is live the receipt is simply not sent; the user re-issues it from the UI. This
     * deliberately avoids automatic retries so an auto-resend can never overlap a manual one and
     * print the same fiscal receipt twice. (The bridge still de-dupes by payment id as a safety net.)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCommitted(PaymentCommittedEvent event) {
        sendReceipt(event.paymentId());
    }

    /** Build + send one fiscal RECEIPT, once, only while a bridge is live. */
    private void sendReceipt(UUID paymentId) {
        if (!handler.isConnected()) {
            log.warn("Bridge offline — fiscal receipt for payment {} not sent (re-issue from the UI when online).",
                    paymentId);
            return;
        }
        String frame;
        try {
            // build + stamp in its own short tx so no DB connection is held across the socket send
            frame = txTemplate.execute(status -> buildAndStampFrame(paymentId));
        } catch (Exception e) {
            log.error("Failed to build fiscal RECEIPT for payment {}: {}", paymentId, e.getMessage(), e);
            return;
        }
        if (frame == null) {
            return;
        }
        try {
            handler.send(frame);
            log.info("Sent fiscal RECEIPT for payment {} (best-effort, no auto-retry).", paymentId);
        } catch (Exception e) {
            log.error("Failed to send fiscal RECEIPT for payment {}: {}", paymentId, e.getMessage(), e);
        }
    }

    /** Within one tx: load the single payment, build its frame, stamp fiscalSentAt, return the frame. */
    private String buildAndStampFrame(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return null;
        }
        Map<UUID, OrderPointEntity> opsById = Map.of();
        Map<UUID, String> ipByIntegration = Map.of();
        OrderPointEntity op = payment.getOrderPointId() == null ? null
                : orderPointRepository.findById(payment.getOrderPointId()).orElse(null);
        if (op != null) {
            opsById = Map.of(op.getId(), op);
            if (op.getCashRegisterId() != null) {
                IntegrationEntity reg = integrationRepository.findById(op.getCashRegisterId()).orElse(null);
                if (reg != null && reg.getIp() != null && !reg.getIp().isBlank()) {
                    ipByIntegration = Map.of(reg.getId(), reg.getIp());
                }
            }
        }
        List<OrderItemEntity> items = orderItemRepository.findByPaymentIdIn(List.of(paymentId));
        Map<UUID, List<OrderItemEntity>> itemsByPayment = items.isEmpty() ? Map.of() : Map.of(paymentId, items);
        Map<UUID, BigDecimal> vatByMenuItem = resolveVat(items);

        String frame;
        try {
            frame = buildReceiptFrame(payment, opsById, ipByIntegration, itemsByPayment, vatByMenuItem);
        } catch (Exception e) {
            log.error("Failed to build RECEIPT for payment {}: {}", paymentId, e.getMessage(), e);
            return null;
        }
        if (frame != null) {
            payment.setFiscalSentAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }
        return frame;
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

        List<Map<String, Object>> lines = new ArrayList<>(items.size() + 1);
        for (OrderItemEntity it : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", plainName(it.getName()));
            m.put("quantity", it.getQuantity());
            m.put("unitPrice", it.getPrice() == null ? BigDecimal.ZERO : it.getPrice());
            m.put("vat", it.getMenuItemId() == null ? null : vatByMenuItem.get(it.getMenuItemId()));
            lines.add(m);
        }
        // Tip (bacsis) as its own line in the VAT 0 category.
        BigDecimal tip = payment.getTip();
        if (tip != null && tip.signum() > 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", "Tips");
            m.put("quantity", 1);
            m.put("unitPrice", tip);
            m.put("vat", BigDecimal.ZERO);
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

    /**
     * Product names are stored as rich text (HTML). Strip the markup — and the small-font
     * description block — to the plain product title on a single line for the fiscal receipt.
     */
    static String plainName(String html) {
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
