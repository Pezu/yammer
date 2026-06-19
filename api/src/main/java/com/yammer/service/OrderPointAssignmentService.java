package com.yammer.service;

import com.yammer.dto.AssignmentRequest;
import com.yammer.dto.OrderPointResponse;
import com.yammer.dto.ParentAssignmentResponse;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.UserEntity;
import com.yammer.dto.MyTableResponse;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.PaymentSummaryResponse;
import com.yammer.dto.TableStatsResponse;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderPointAssignmentService {

    private final OrderPointAssignmentRepository assignmentRepository;
    private final OrderPointRepository orderPointRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUserProvider currentUser;
    private final AccessGuard accessGuard;
    private final OrderResponseAssembler orderResponseAssembler;

    /** Parent name = the part of an order-point name before the first dot ("M80.1" → "M80", "B1" → "B1"). */
    public static String parentOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.indexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    /** Every distinct parent at the location, each with the users currently assigned to it. */
    @Transactional(readOnly = true)
    public List<ParentAssignmentResponse> list(UUID locationId, UUID eventId) {
        requireAccessibleLocation(locationId);

        // distinct parents (preserve order-point name order), scoped to the event when given
        List<OrderPointEntity> ops =
                orderPointRepository.findByLocationAndOptionalEventOrderByName(locationId, eventId);
        Set<String> parents = new LinkedHashSet<>();
        for (var op : ops) {
            parents.add(parentOf(op.getName()));
        }

        List<OrderPointAssignmentEntity> assignments = eventId != null
                ? assignmentRepository.findByLocationIdAndEventId(locationId, eventId)
                : assignmentRepository.findByLocationId(locationId);
        Map<String, List<UUID>> byParent = new LinkedHashMap<>();
        for (var a : assignments) {
            byParent.computeIfAbsent(a.getParentName(), k -> new ArrayList<>()).add(a.getUserId());
        }

        return parents.stream()
                .map(p -> new ParentAssignmentResponse(p, byParent.getOrDefault(p, List.of())))
                .toList();
    }

    /** Order points whose parent the current user is assigned to (across all their assignments). */
    @Transactional(readOnly = true)
    public List<MyTableResponse> myOrderPoints() {
        List<OrderPointEntity> ops = assignedOrderPoints(currentUser.require());
        if (ops.isEmpty()) {
            return List.of();
        }

        // bill state per order point, aggregated in the DB: no lines → EMPTY, any unpaid → UNPAID, else PAID.
        List<UUID> opIds = ops.stream().map(OrderPointEntity::getId).toList();
        Map<UUID, OrderItemRepository.OrderPointItemAgg> agg = orderItemRepository.aggregateByOrderPoint(opIds)
                .stream()
                .collect(Collectors.toMap(OrderItemRepository.OrderPointItemAgg::getOpId, a -> a));

        return ops.stream()
                .map(op -> MyTableResponse.from(op, billStatus(agg.get(op.getId()))))
                .toList();
    }

    /** EMPTY when there are no lines, UNPAID when any line is unsettled, else PAID. */
    private static String billStatus(OrderItemRepository.OrderPointItemAgg agg) {
        if (agg == null || agg.getItems() == 0) {
            return "EMPTY";
        }
        return agg.getUnpaidCount() > 0 ? "UNPAID" : "PAID";
    }

    /** Per-table takings (card/cash paid + tips, and unpaid) for the current user's tables. */
    @Transactional(readOnly = true)
    public List<TableStatsResponse> myStats() {
        List<OrderPointEntity> ops = assignedOrderPoints(currentUser.require());
        if (ops.isEmpty()) {
            return List.of();
        }
        List<UUID> opIds = ops.stream().map(OrderPointEntity::getId).toList();

        // ordered/unpaid/settled per order point and paid/tip per order point — both aggregated in the DB.
        Map<UUID, OrderItemRepository.OrderPointItemAgg> itemAgg = orderItemRepository.aggregateByOrderPoint(opIds)
                .stream()
                .collect(Collectors.toMap(OrderItemRepository.OrderPointItemAgg::getOpId, a -> a));
        Map<UUID, PaymentRepository.OrderPointPaymentAgg> payAgg = paymentRepository.aggregateByOrderPoint(opIds)
                .stream()
                .collect(Collectors.toMap(PaymentRepository.OrderPointPaymentAgg::getOpId, a -> a));

        List<TableStatsResponse> result = new ArrayList<>(ops.size());
        for (OrderPointEntity op : ops) {
            OrderItemRepository.OrderPointItemAgg i = itemAgg.get(op.getId());
            PaymentRepository.OrderPointPaymentAgg p = payAgg.get(op.getId());
            result.add(new TableStatsResponse(
                    op.getId(),
                    op.getName(),
                    p != null ? p.getPaidCard() : BigDecimal.ZERO,
                    p != null ? p.getPaidCash() : BigDecimal.ZERO,
                    p != null ? p.getTipCard() : BigDecimal.ZERO,
                    p != null ? p.getTipCash() : BigDecimal.ZERO,
                    i != null ? i.getUnpaid() : BigDecimal.ZERO,
                    i != null ? i.getSettled() : BigDecimal.ZERO,
                    op.isProtocol()));
        }
        return result;
    }

    /** All payments at the current user's tables (newest first) for the Payments page. */
    @Transactional(readOnly = true)
    public List<PaymentSummaryResponse> myPayments() {
        List<OrderPointEntity> ops = assignedOrderPoints(currentUser.require());
        if (ops.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = ops.stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        // Filtering (exclude PROTOCOL) and newest-first ordering happen in SQL, not the JVM.
        return paymentRepository
                .findByOrderPointIdInAndMethodNotOrderByCreatedAtDesc(
                        ops.stream().map(OrderPointEntity::getId).toList(), PaymentMethod.PROTOCOL).stream()
                .map(p -> new PaymentSummaryResponse(
                        p.getId(),
                        p.getOrderPointId(),
                        names.get(p.getOrderPointId()),
                        p.getAmount(),
                        p.getMethod(),
                        p.getTip(),
                        p.getFiscalStatus(),
                        p.getReceiptNumber(),
                        p.getCreatedAt()))
                .toList();
    }

    /**
     * Service kanban board for the current user: undelivered orders placed on order points whose
     * service point (order_point.service_order_point_id) is one this user is assigned to.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> serviceBoard() {
        // The order points this service user is assigned to (their service stations).
        List<OrderPointEntity> myServicePoints = assignedOrderPoints(currentUser.require());
        if (myServicePoints.isEmpty()) {
            return List.of();
        }
        Set<UUID> servicePointIds = myServicePoints.stream()
                .map(OrderPointEntity::getId)
                .collect(Collectors.toSet());

        // The order points (tables) routed to those service stations.
        List<OrderPointEntity> sources = orderPointRepository.findByServiceOrderPointIdIn(servicePointIds);
        if (sources.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = sources.stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));

        List<OrderEntity> orders = orderRepository.findByOrderPointIdInAndStatusInOrderByCreatedAtDesc(
                names.keySet(), OrderService.BOARD_STATUSES);
        return orderResponseAssembler.assemble(orders, names);
    }

    /**
     * Undelivered orders at the current user's tables, newest first. When {@code status} is one of
     * the board statuses (ORDERED / READY) only that status is returned; otherwise both.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(String status) {
        String wanted = status == null ? null : status.trim().toUpperCase();
        Set<String> statuses = OrderService.BOARD_STATUSES.contains(wanted)
                ? Set.of(wanted)
                : OrderService.BOARD_STATUSES;

        List<OrderPointEntity> ops = assignedOrderPoints(currentUser.require());
        if (ops.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = ops.stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        List<OrderEntity> orders = orderRepository.findByOrderPointIdInAndStatusInOrderByCreatedAtDesc(
                names.keySet(), statuses);
        return orderResponseAssembler.assemble(orders, names);
    }

    /** Retry fiscalization for a failed payment. For now this only logs the payment details. */
    public void retryFiscal(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
        List<OrderItemEntity> items = orderItemRepository.findByPaymentId(paymentId);
        String lines = items.stream()
                .map(i -> "%dx %s @ %s".formatted(
                        i.getQuantity(),
                        i.getName(),
                        i.getPrice() == null ? BigDecimal.ZERO : i.getPrice()))
                .collect(Collectors.joining(", "));
        log.info("Fiscal retry requested for payment {} (method={}, amount={}, tip={}): items=[{}]",
                paymentId, payment.getMethod(), payment.getAmount(), payment.getTip(), lines);
    }

    /** The order points whose parent the given user is assigned to (name-ordered per location). */
    private List<OrderPointEntity> assignedOrderPoints(UserPrincipal me) {
        UserEntity user = userRepository.findByUsername(me.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // group the user's assigned parents by (location, event)
        Map<UUID, Map<UUID, Set<String>>> parentsByLocEvent = new LinkedHashMap<>();
        for (var a : assignmentRepository.findByUserId(user.getId())) {
            parentsByLocEvent
                    .computeIfAbsent(a.getLocationId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(a.getEventId(), k -> new LinkedHashSet<>())
                    .add(a.getParentName());
        }
        List<OrderPointEntity> ops = new ArrayList<>();
        for (var locEntry : parentsByLocEvent.entrySet()) {
            UUID locationId = locEntry.getKey();
            for (var evtEntry : locEntry.getValue().entrySet()) {
                UUID eventId = evtEntry.getKey();
                Set<String> parents = evtEntry.getValue();
                List<OrderPointEntity> candidates =
                        orderPointRepository.findByLocationAndOptionalEventOrderByName(locationId, eventId);
                for (OrderPointEntity op : candidates) {
                    if (parents.contains(parentOf(op.getName()))) {
                        ops.add(op);
                    }
                }
            }
        }
        return ops;
    }

    /** Replace the set of users assigned to one parent. */
    public ParentAssignmentResponse set(AssignmentRequest request) {
        LocationEntity location = requireAccessibleLocation(request.locationId());
        String parent = request.parentName().trim();

        List<UUID> userIds = request.userIds() == null
                ? List.of()
                : request.userIds().stream().distinct().toList();

        // every assigned user must belong to the location's client
        if (!userIds.isEmpty()) {
            Set<UUID> valid = userRepository.findByClientIdOrderByUsername(location.getClientId()).stream()
                    .map(UserEntity::getId)
                    .collect(Collectors.toSet());
            userIds.stream().filter(id -> !valid.contains(id)).findFirst().ifPresent(id -> {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "User does not belong to this location's client: " + id);
            });
        }

        // diff against existing (avoids delete+reinsert hitting the unique constraint)
        UUID eventId = request.eventId();
        List<OrderPointAssignmentEntity> existing = eventId != null
                ? assignmentRepository.findByLocationIdAndEventIdAndParentName(request.locationId(), eventId, parent)
                : assignmentRepository.findByLocationIdAndParentName(request.locationId(), parent);
        Set<UUID> target = new LinkedHashSet<>(userIds);
        Set<UUID> current = existing.stream()
                .map(OrderPointAssignmentEntity::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // remove existing − target, insert target − current — batched, not row-by-row.
        assignmentRepository.deleteAll(existing.stream()
                .filter(e -> !target.contains(e.getUserId()))
                .toList());
        assignmentRepository.saveAll(target.stream()
                .filter(id -> !current.contains(id))
                .map(id -> newAssignment(request.locationId(), eventId, parent, id))
                .toList());
        return new ParentAssignmentResponse(parent, userIds);
    }

    private static OrderPointAssignmentEntity newAssignment(UUID locationId, UUID eventId, String parent, UUID userId) {
        OrderPointAssignmentEntity e = new OrderPointAssignmentEntity();
        e.setLocationId(locationId);
        e.setEventId(eventId);
        e.setParentName(parent);
        e.setUserId(userId);
        return e;
    }

    private LocationEntity requireAccessibleLocation(UUID locationId) {
        return accessGuard.requireAccessibleLocation(locationId);
    }
}
