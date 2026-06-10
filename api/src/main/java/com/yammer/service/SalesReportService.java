package com.yammer.service;

import com.yammer.dto.SalesIntervalRow;
import com.yammer.dto.SalesSummaryResponse;
import com.yammer.dto.TableReportRow;
import com.yammer.dto.WaiterReportRow;
import com.yammer.dto.WaiterTableRow;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the 10-minute-bucketed Sales report (amount ordered, amount paid, order count). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private static final int BUCKET_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUserProvider currentUser;

    public List<SalesIntervalRow> intervals() {
        // Reporting window: since yesterday afternoon (12:00) up to now.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = now.toLocalDate().minusDays(1).atTime(12, 0);
        LocalDateTime lastBucket = bucketOf(now);

        List<UUID> opIds = scopedOrderPointIds();

        // Pre-seed every 10-minute bucket in the window so the timeline is continuous.
        Map<LocalDateTime, BigDecimal> orderedByBucket = new TreeMap<>();
        Map<LocalDateTime, BigDecimal> paidByBucket = new TreeMap<>();
        Map<LocalDateTime, BigDecimal> protocolByBucket = new TreeMap<>();
        Map<LocalDateTime, Long> countByBucket = new TreeMap<>();
        for (LocalDateTime b = rangeStart; !b.isAfter(lastBucket); b = b.plusMinutes(BUCKET_MINUTES)) {
            orderedByBucket.put(b, BigDecimal.ZERO);
            paidByBucket.put(b, BigDecimal.ZERO);
            protocolByBucket.put(b, BigDecimal.ZERO);
            countByBucket.put(b, 0L);
        }

        if (!opIds.isEmpty()) {
            // Let the DB filter to the report window instead of scanning full history.
            List<OrderEntity> orders =
                    orderRepository.findByOrderPointIdInAndCreatedAtBetween(opIds, rangeStart, now);
            Map<UUID, List<OrderItemEntity>> itemsByOrder = orders.isEmpty()
                    ? Map.of()
                    : orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
                            .stream()
                            .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));

            for (OrderEntity order : orders) {
                LocalDateTime bucket = bucketOf(order.getCreatedAt());
                BigDecimal total = BigDecimal.ZERO;
                for (OrderItemEntity item : itemsByOrder.getOrDefault(order.getId(), List.of())) {
                    total = total.add(lineTotal(item));
                }
                orderedByBucket.merge(bucket, total, BigDecimal::add);
                countByBucket.merge(bucket, 1L, Long::sum);
            }

            List<PaymentEntity> payments =
                    paymentRepository.findByOrderPointIdInAndCreatedAtBetween(opIds, rangeStart, now);
            // Batch-load the lines settled by protocol payments (avoids a query per payment).
            List<UUID> protocolPaymentIds = payments.stream()
                    .filter(p -> p.getMethod() == PaymentMethod.PROTOCOL)
                    .map(PaymentEntity::getId)
                    .toList();
            Map<UUID, List<OrderItemEntity>> itemsByPayment = protocolPaymentIds.isEmpty()
                    ? Map.of()
                    : orderItemRepository.findByPaymentIdIn(protocolPaymentIds).stream()
                            .collect(Collectors.groupingBy(OrderItemEntity::getPaymentId));

            for (PaymentEntity payment : payments) {
                LocalDateTime bucket = bucketOf(payment.getCreatedAt());
                if (payment.getMethod() == PaymentMethod.PROTOCOL) {
                    // protocol payments store amount 0; the "settled" value is the lines they cover
                    BigDecimal settled = BigDecimal.ZERO;
                    for (OrderItemEntity item : itemsByPayment.getOrDefault(payment.getId(), List.of())) {
                        settled = settled.add(lineTotal(item));
                    }
                    protocolByBucket.merge(bucket, settled, BigDecimal::add);
                } else {
                    BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
                    paidByBucket.merge(bucket, amount, BigDecimal::add);
                }
            }
        }

        return orderedByBucket.keySet().stream()
                .map(b -> new SalesIntervalRow(
                        b,
                        orderedByBucket.get(b),
                        paidByBucket.getOrDefault(b, BigDecimal.ZERO),
                        protocolByBucket.getOrDefault(b, BigDecimal.ZERO),
                        countByBucket.getOrDefault(b, 0L)))
                .toList();
    }

    /**
     * Totals over the report window: total sales, paid (tips excluded), settled for protocol,
     * and what's still unpaid — split by whether the order point is a protocol point.
     */
    public SalesSummaryResponse summary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = now.toLocalDate().minusDays(1).atTime(12, 0);

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalProtocol = BigDecimal.ZERO;
        BigDecimal remainingToPay = BigDecimal.ZERO;
        BigDecimal remainingProtocol = BigDecimal.ZERO;

        List<UUID> opIds = scopedOrderPointIds();
        if (!opIds.isEmpty()) {
            Map<UUID, Boolean> protocolByOp = new HashMap<>();
            for (OrderPointEntity op : orderPointRepository.findAllById(opIds)) {
                protocolByOp.put(op.getId(), op.isProtocol());
            }
            List<OrderEntity> orders =
                    orderRepository.findByOrderPointIdInAndCreatedAtBetween(opIds, rangeStart, now);
            Map<UUID, UUID> opByOrder = new HashMap<>();
            for (OrderEntity o : orders) {
                opByOrder.put(o.getId(), o.getOrderPointId());
            }
            List<OrderItemEntity> items = orders.isEmpty()
                    ? List.of()
                    : orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());

            Set<UUID> paymentIds = items.stream()
                    .map(OrderItemEntity::getPaymentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, PaymentMethod> methodById = paymentIds.isEmpty()
                    ? Map.of()
                    : paymentRepository.findAllById(paymentIds).stream()
                            .collect(Collectors.toMap(PaymentEntity::getId, PaymentEntity::getMethod));

            for (OrderItemEntity item : items) {
                BigDecimal lt = lineTotal(item);
                totalSales = totalSales.add(lt);
                boolean opProtocol = protocolByOp.getOrDefault(opByOrder.get(item.getOrderId()), false);
                if (item.getPaymentId() == null) {
                    if (opProtocol) {
                        remainingProtocol = remainingProtocol.add(lt);
                    } else {
                        remainingToPay = remainingToPay.add(lt);
                    }
                } else if (methodById.get(item.getPaymentId()) == PaymentMethod.PROTOCOL) {
                    totalProtocol = totalProtocol.add(lt);
                } else {
                    totalPaid = totalPaid.add(lt);
                }
            }
        }
        return new SalesSummaryResponse(
                totalSales, totalPaid, totalProtocol, remainingToPay, remainingProtocol);
    }

    /**
     * Per order point over the window: ordered, cash, card, protocol-settled, and what's
     * unpaid — split into "to pay" (non-protocol points) and "protocol" (protocol points).
     */
    public List<TableReportRow> tables() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = now.toLocalDate().minusDays(1).atTime(12, 0);

        List<UUID> opIds = scopedOrderPointIds();
        if (opIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Boolean> isProtocol = new HashMap<>();
        for (OrderPointEntity op : orderPointRepository.findAllById(opIds)) {
            names.put(op.getId(), op.getName());
            isProtocol.put(op.getId(), op.isProtocol());
        }

        Map<UUID, BigDecimal> ordered = new HashMap<>();
        Map<UUID, BigDecimal> cash = new HashMap<>();
        Map<UUID, BigDecimal> card = new HashMap<>();
        Map<UUID, BigDecimal> protocol = new HashMap<>();
        Map<UUID, BigDecimal> remaining = new HashMap<>();
        Map<UUID, BigDecimal> remainingProtocol = new HashMap<>();

        // cash/card payment amounts + a method lookup for protocol-settled items
        Map<UUID, PaymentMethod> methodById = new HashMap<>();
        for (PaymentEntity p :
                paymentRepository.findByOrderPointIdInAndCreatedAtBetween(opIds, rangeStart, now)) {
            methodById.put(p.getId(), p.getMethod());
            BigDecimal amt = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
            if (p.getMethod() == PaymentMethod.CASH) {
                cash.merge(p.getOrderPointId(), amt, BigDecimal::add);
            } else if (p.getMethod() == PaymentMethod.CARD) {
                card.merge(p.getOrderPointId(), amt, BigDecimal::add);
            }
        }

        List<OrderEntity> orders =
                orderRepository.findByOrderPointIdInAndCreatedAtBetween(opIds, rangeStart, now);
        Map<UUID, UUID> opByOrder = new HashMap<>();
        for (OrderEntity o : orders) {
            opByOrder.put(o.getId(), o.getOrderPointId());
        }
        if (!orders.isEmpty()) {
            for (OrderItemEntity item :
                    orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())) {
                UUID op = opByOrder.get(item.getOrderId());
                BigDecimal lt = lineTotal(item);
                ordered.merge(op, lt, BigDecimal::add);
                if (item.getPaymentId() == null) {
                    if (Boolean.TRUE.equals(isProtocol.get(op))) {
                        remainingProtocol.merge(op, lt, BigDecimal::add);
                    } else {
                        remaining.merge(op, lt, BigDecimal::add);
                    }
                } else if (methodById.get(item.getPaymentId()) == PaymentMethod.PROTOCOL) {
                    protocol.merge(op, lt, BigDecimal::add);
                }
            }
        }

        return ordered.keySet().stream()
                .map(op -> {
                    BigDecimal ord = ordered.getOrDefault(op, BigDecimal.ZERO);
                    boolean prot = Boolean.TRUE.equals(isProtocol.get(op));
                    return new TableReportRow(
                            names.getOrDefault(op, "?"),
                            ord,
                            prot ? BigDecimal.ZERO : ord,
                            prot ? ord : BigDecimal.ZERO,
                            cash.getOrDefault(op, BigDecimal.ZERO),
                            card.getOrDefault(op, BigDecimal.ZERO),
                            protocol.getOrDefault(op, BigDecimal.ZERO),
                            remaining.getOrDefault(op, BigDecimal.ZERO),
                            remainingProtocol.getOrDefault(op, BigDecimal.ZERO));
                })
                .sorted(Comparator.comparing(TableReportRow::ordered).reversed())
                .toList();
    }

    /** Per waiter over the window: number of orders placed and their sales value. */
    public List<WaiterReportRow> waiters() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = now.toLocalDate().minusDays(1).atTime(12, 0);

        List<UUID> opIds = scopedOrderPointIds();
        if (opIds.isEmpty()) {
            return List.of();
        }
        List<OrderEntity> orders =
                orderRepository.findByOrderPointIdInAndCreatedAtBetween(opIds, rangeStart, now);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> totalByOrder = new HashMap<>();
        for (OrderItemEntity item :
                orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())) {
            totalByOrder.merge(item.getOrderId(), lineTotal(item), BigDecimal::add);
        }

        Map<String, long[]> countByWaiter = new HashMap<>();
        Map<String, BigDecimal> salesByWaiter = new HashMap<>();
        for (OrderEntity o : orders) {
            String waiter = o.getCreatedBy() == null || o.getCreatedBy().isBlank() ? "—" : o.getCreatedBy();
            countByWaiter.computeIfAbsent(waiter, k -> new long[1])[0]++;
            salesByWaiter.merge(waiter, totalByOrder.getOrDefault(o.getId(), BigDecimal.ZERO), BigDecimal::add);
        }

        return countByWaiter.keySet().stream()
                .map(w -> new WaiterReportRow(
                        w, countByWaiter.get(w)[0], salesByWaiter.getOrDefault(w, BigDecimal.ZERO)))
                .sorted(Comparator.comparing(WaiterReportRow::sales).reversed())
                .toList();
    }

    /**
     * Per waiter and order point over the window: ordered, paid (cash/card), tip, and
     * protocol-settled — all attributed to the waiter who placed the order.
     */
    public List<WaiterTableRow> waiterTables() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = now.toLocalDate().minusDays(1).atTime(12, 0);

        List<UUID> opIds = scopedOrderPointIds();
        if (opIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> opNames = new HashMap<>();
        for (OrderPointEntity op : orderPointRepository.findAllById(opIds)) {
            opNames.put(op.getId(), op.getName());
        }
        List<OrderEntity> orders =
                orderRepository.findByOrderPointIdInAndCreatedAtBetween(opIds, rangeStart, now);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, OrderEntity> orderById = new HashMap<>();
        for (OrderEntity o : orders) {
            orderById.put(o.getId(), o);
        }
        List<OrderItemEntity> items =
                orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());
        Map<UUID, PaymentEntity> paymentById = new HashMap<>();
        Set<UUID> payIds = items.stream()
                .map(OrderItemEntity::getPaymentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!payIds.isEmpty()) {
            for (PaymentEntity p : paymentRepository.findAllById(payIds)) {
                paymentById.put(p.getId(), p);
            }
        }

        // accumulate per (waiter | orderPointId)
        record Key(String waiter, UUID opId) {}
        Map<Key, BigDecimal> ordered = new HashMap<>();
        Map<Key, BigDecimal> paid = new HashMap<>();
        Map<Key, BigDecimal> protocol = new HashMap<>();
        Map<Key, Set<UUID>> tipPayments = new HashMap<>();

        for (OrderItemEntity item : items) {
            OrderEntity o = orderById.get(item.getOrderId());
            if (o == null) {
                continue;
            }
            String waiter = o.getCreatedBy() == null || o.getCreatedBy().isBlank() ? "—" : o.getCreatedBy();
            Key key = new Key(waiter, o.getOrderPointId());
            BigDecimal lt = lineTotal(item);
            ordered.merge(key, lt, BigDecimal::add);
            if (item.getPaymentId() != null) {
                PaymentEntity p = paymentById.get(item.getPaymentId());
                if (p != null && p.getMethod() == PaymentMethod.PROTOCOL) {
                    protocol.merge(key, lt, BigDecimal::add);
                } else {
                    paid.merge(key, lt, BigDecimal::add);
                    tipPayments.computeIfAbsent(key, k -> new HashSet<>()).add(item.getPaymentId());
                }
            }
        }

        return ordered.keySet().stream()
                .map(key -> {
                    BigDecimal tip = BigDecimal.ZERO;
                    for (UUID pid : tipPayments.getOrDefault(key, Set.of())) {
                        PaymentEntity p = paymentById.get(pid);
                        if (p != null && p.getTip() != null) {
                            tip = tip.add(p.getTip());
                        }
                    }
                    return new WaiterTableRow(
                            key.waiter(),
                            opNames.getOrDefault(key.opId(), "?"),
                            ordered.getOrDefault(key, BigDecimal.ZERO),
                            paid.getOrDefault(key, BigDecimal.ZERO),
                            tip,
                            protocol.getOrDefault(key, BigDecimal.ZERO));
                })
                .sorted(Comparator.comparing(WaiterTableRow::waiter)
                        .thenComparing(Comparator.comparing(WaiterTableRow::ordered).reversed()))
                .toList();
    }

    /** price × quantity for one line (price may be null). */
    private BigDecimal lineTotal(OrderItemEntity item) {
        BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        return price.multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    /** Floors a timestamp down to the start of its 10-minute bucket. */
    private LocalDateTime bucketOf(LocalDateTime ts) {
        return ts.withSecond(0).withNano(0)
                .withMinute((ts.getMinute() / BUCKET_MINUTES) * BUCKET_MINUTES);
    }

    /** Order-point ids visible to the current user: SUPER → all; otherwise their client's. */
    private List<UUID> scopedOrderPointIds() {
        UserPrincipal me = currentUser.require();
        if (me.isSuper()) {
            return orderPointRepository.findAll().stream().map(OrderPointEntity::getId).toList();
        }
        if (me.clientId() == null) {
            return List.of();
        }
        List<UUID> locationIds = locationRepository.findByClientIdOrderByName(me.clientId()).stream()
                .map(LocationEntity::getId)
                .toList();
        if (locationIds.isEmpty()) {
            return List.of();
        }
        return orderPointRepository.findByLocationIdIn(locationIds).stream()
                .map(OrderPointEntity::getId)
                .toList();
    }
}
