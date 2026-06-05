package com.yammer.service;

import com.yammer.dto.SalesIntervalRow;
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
import java.util.List;
import java.util.Map;
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
