package com.yammer.service;

import com.yammer.dto.FinalReportRow;
import com.yammer.dto.PagedResponse;
import com.yammer.dto.PaymentFilterOptions;
import com.yammer.dto.PaymentReportRow;
import com.yammer.dto.SalesIntervalRow;
import com.yammer.dto.SalesSummaryResponse;
import com.yammer.dto.TableReportRow;
import com.yammer.dto.WaiterReportRow;
import com.yammer.dto.WaiterTableRow;
import com.yammer.entity.FiscalStatus;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.ReportRepository;
import com.yammer.security.AccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard reports. Every report is per-event and aggregated in the database
 * ({@link ReportRepository}) — each call returns at most a handful of rows (per order point, per
 * waiter, or per 10-minute bucket) rather than loading the event's full order/payment history into
 * the JVM. A {@code null} event yields empty results (the dashboard always selects an event).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private static final int BUCKET_MINUTES = 10;

    private final ReportRepository reportRepository;
    private final OrderPointRepository orderPointRepository;
    private final PaymentRepository paymentRepository;
    private final AccessGuard accessGuard;

    /** Sales bucketed into 10-minute intervals (amount ordered, amount paid, protocol, order count). */
    public List<SalesIntervalRow> intervals(UUID eventId) {
        if (!accessible(eventId)) {
            return List.of();
        }
        Map<LocalDateTime, BigDecimal> ordered = new HashMap<>();
        Map<LocalDateTime, Long> counts = new HashMap<>();
        for (ReportRepository.BucketOrderedRow r : reportRepository.orderedByBucketForEvent(eventId)) {
            ordered.put(r.getBucket(), r.getOrdered());
            counts.put(r.getBucket(), r.getCnt());
        }
        Map<LocalDateTime, BigDecimal> paid = toBucketMap(reportRepository.paidByBucketForEvent(eventId));
        Map<LocalDateTime, BigDecimal> protocol = toBucketMap(reportRepository.protocolByBucketForEvent(eventId));

        TreeSet<LocalDateTime> buckets = new TreeSet<>();
        buckets.addAll(ordered.keySet());
        buckets.addAll(paid.keySet());
        buckets.addAll(protocol.keySet());
        if (buckets.isEmpty()) {
            return List.of();
        }

        // Continuous timeline from first to last active bucket, so the chart has no gaps.
        List<SalesIntervalRow> rows = new ArrayList<>();
        for (LocalDateTime b = buckets.first(); !b.isAfter(buckets.last()); b = b.plusMinutes(BUCKET_MINUTES)) {
            rows.add(new SalesIntervalRow(
                    b,
                    ordered.getOrDefault(b, BigDecimal.ZERO),
                    paid.getOrDefault(b, BigDecimal.ZERO),
                    protocol.getOrDefault(b, BigDecimal.ZERO),
                    counts.getOrDefault(b, 0L)));
        }
        return rows;
    }

    /** Totals: sales, paid (tips excluded), protocol-settled, and what's still unpaid. */
    public SalesSummaryResponse summary(UUID eventId) {
        if (!accessible(eventId)) {
            return new SalesSummaryResponse(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        ReportRepository.SummaryRow r = reportRepository.summaryByEvent(eventId);
        return new SalesSummaryResponse(
                r.getTotalSales(), r.getTotalPaid(), r.getTotalProtocol(),
                r.getRemainingToPay(), r.getRemainingProtocol());
    }

    /** Per order point: ordered, cash, card, protocol-settled, and what's unpaid. */
    public List<TableReportRow> tables(UUID eventId) {
        if (!accessible(eventId)) {
            return List.of();
        }
        Map<UUID, ReportRepository.TablePayRow> payByOp = reportRepository.tablePaymentsByEvent(eventId).stream()
                .collect(Collectors.toMap(ReportRepository.TablePayRow::getOpId, Function.identity()));

        return reportRepository.tableItemsByEvent(eventId).stream()
                .map(r -> {
                    BigDecimal ord = r.getOrdered();
                    // Ordered is paid-side or protocol-side per the order point's nature
                    // (a protocol point comps all its orders).
                    boolean prot = r.getProtocolPoint();
                    ReportRepository.TablePayRow pay = payByOp.get(r.getOpId());
                    return new TableReportRow(
                            r.getOpName(),
                            ord,
                            prot ? BigDecimal.ZERO : ord,
                            prot ? ord : BigDecimal.ZERO,
                            pay != null ? pay.getCash() : BigDecimal.ZERO,
                            pay != null ? pay.getCard() : BigDecimal.ZERO,
                            r.getProtocolSettled(),
                            r.getRemaining(),
                            r.getRemainingProtocol());
                })
                .sorted(Comparator.comparing(TableReportRow::ordered).reversed())
                .toList();
    }

    /** Per waiter: number of orders placed and their sales value (highest first). */
    public List<WaiterReportRow> waiters(UUID eventId) {
        if (!accessible(eventId)) {
            return List.of();
        }
        return reportRepository.waitersByEvent(eventId).stream()
                .map(r -> new WaiterReportRow(
                        r.getWaiter(), r.getOrders(), r.getSales(),
                        r.getUnsettledPaid(), r.getUnsettledProtocol()))
                .toList();
    }

    /** Per waiter and order point: ordered, paid, tip, protocol (tip summed per distinct payment). */
    public List<WaiterTableRow> waiterTables(UUID eventId) {
        if (!accessible(eventId)) {
            return List.of();
        }
        Map<String, BigDecimal> tipByKey = new HashMap<>();
        for (ReportRepository.WaiterTableTipRow t : reportRepository.waiterTableTipsByEvent(eventId)) {
            tipByKey.merge(waiterOpKey(t.getWaiter(), t.getOpId()), t.getTip(), BigDecimal::add);
        }
        return reportRepository.waiterTablesByEvent(eventId).stream()
                .map(r -> new WaiterTableRow(
                        r.getWaiter(),
                        r.getOpName(),
                        r.getOrdered(),
                        r.getPaid(),
                        tipByKey.getOrDefault(waiterOpKey(r.getWaiter(), r.getOpId()), BigDecimal.ZERO),
                        r.getProtocolSettled()))
                .toList();
    }

    /** Final report: per user + order point, card/cash paid and tip (protocol excluded). */
    public List<FinalReportRow> finalReport(UUID eventId) {
        if (!accessible(eventId)) {
            return List.of();
        }
        return reportRepository.finalReportByEvent(eventId).stream()
                .map(r -> new FinalReportRow(
                        r.getUserName(),
                        r.getTableName(),
                        r.getPaidCard(),
                        r.getPaidCash(),
                        r.getTipCard(),
                        r.getTipCash()))
                .toList();
    }

    /**
     * One page of the event's payments (newest first), filtered server-side by method / order point
     * / user / fiscal status — backs the payments report. Mirrors the orders report's paging model.
     */
    public PagedResponse<PaymentReportRow> paymentsPaged(
            int page, int size, UUID eventId, String method, UUID orderPointId,
            String createdBy, String fiscalStatus) {
        if (!accessible(eventId)) {
            return new PagedResponse<>(List.of(), 0, page, size);
        }
        PaymentMethod methodEnum = parseEnum(PaymentMethod.class, method);
        FiscalStatus fiscalEnum = parseEnum(FiscalStatus.class, fiscalStatus);
        Specification<PaymentEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("eventId"), eventId));
            if (methodEnum != null) {
                ps.add(cb.equal(root.get("method"), methodEnum));
            }
            if (orderPointId != null) {
                ps.add(cb.equal(root.get("orderPointId"), orderPointId));
            }
            if (createdBy != null && !createdBy.isBlank()) {
                ps.add(cb.equal(root.get("createdBy"), createdBy));
            }
            if (fiscalEnum != null) {
                ps.add(cb.equal(root.get("fiscalStatus"), fiscalEnum));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentEntity> result = paymentRepository.findAll(spec, pageable);

        Set<UUID> opIds = result.getContent().stream()
                .map(PaymentEntity::getOrderPointId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = orderPointRepository.findAllById(opIds).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));

        List<PaymentReportRow> rows = result.getContent().stream()
                .map(p -> new PaymentReportRow(
                        p.getId(),
                        p.getCreatedAt(),
                        names.getOrDefault(p.getOrderPointId(), ""),
                        p.getMethod() == null ? "" : p.getMethod().name(),
                        p.getAmount(),
                        p.getTip(),
                        p.getFiscalStatus() == null ? "" : p.getFiscalStatus().name(),
                        p.getReceiptNumber(),
                        p.getCreatedBy()))
                .toList();
        return new PagedResponse<>(rows, result.getTotalElements(), page, size);
    }

    /** Distinct filter values for the payments report (method / order point / user / fiscal). */
    public PaymentFilterOptions paymentFilterOptions(UUID eventId) {
        if (!accessible(eventId)) {
            return new PaymentFilterOptions(List.of(), List.of(), List.of(), List.of());
        }
        List<String> methods = paymentRepository.distinctMethodsByEvent(eventId).stream()
                .map(Enum::name).sorted().toList();
        // Fiscal status is a small fixed set — always offer all of them, not just the present ones.
        List<String> fiscal = Arrays.stream(FiscalStatus.values()).map(Enum::name).sorted().toList();
        List<String> users = paymentRepository.distinctUsersByEvent(eventId).stream().sorted().toList();
        List<PaymentFilterOptions.OrderPointOption> ops = orderPointRepository
                .findByEventIdOrderByName(eventId).stream()
                .map(o -> new PaymentFilterOptions.OrderPointOption(o.getId(), o.getName()))
                .toList();
        return new PaymentFilterOptions(methods, ops, users, fiscal);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static Map<LocalDateTime, BigDecimal> toBucketMap(List<ReportRepository.BucketValueRow> rows) {
        Map<LocalDateTime, BigDecimal> m = new HashMap<>();
        for (ReportRepository.BucketValueRow r : rows) {
            m.put(r.getBucket(), r.getValue());
        }
        return m;
    }

    /** Composite key for the (waiter, order-point) tip lookup — op id first to avoid label collisions. */
    private static String waiterOpKey(String waiter, UUID opId) {
        return opId + " " + waiter;
    }

    /**
     * The caller may see this event iff at least one of its order points is in their visible set
     * (an event belongs to one client, so this is an all-or-nothing tenant gate). A {@code null}
     * event is never accessible — the dashboard must pick one.
     */
    private boolean accessible(UUID eventId) {
        if (eventId == null) {
            return false;
        }
        Set<UUID> eventOps = new HashSet<>(orderPointRepository.findIdsByEventId(eventId));
        return accessGuard.visibleOrderPointIds().stream().anyMatch(eventOps::contains);
    }
}
