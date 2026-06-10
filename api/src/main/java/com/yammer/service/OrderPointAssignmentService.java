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
import java.util.Comparator;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUserProvider currentUser;

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
        List<OrderPointEntity> ops = eventId != null
                ? orderPointRepository.findByLocationIdAndEventIdOrderByName(locationId, eventId)
                : orderPointRepository.findByLocationIdOrderByName(locationId);
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

        // bill state per order point: does it have items? any unpaid?
        List<UUID> opIds = ops.stream().map(OrderPointEntity::getId).toList();
        List<OrderEntity> orders = orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(opIds);
        Map<UUID, UUID> orderToOp = new HashMap<>();
        for (OrderEntity o : orders) {
            orderToOp.put(o.getId(), o.getOrderPointId());
        }
        List<OrderItemEntity> items = orders.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());
        Map<UUID, boolean[]> flags = new HashMap<>(); // [hasItems, hasUnpaid]
        for (OrderItemEntity it : items) {
            UUID opId = orderToOp.get(it.getOrderId());
            if (opId == null) {
                continue;
            }
            boolean[] f = flags.computeIfAbsent(opId, k -> new boolean[2]);
            f[0] = true;
            if (it.getPaymentId() == null) {
                f[1] = true;
            }
        }

        return ops.stream()
                .map(op -> {
                    boolean[] f = flags.get(op.getId());
                    String status = (f == null || !f[0]) ? "EMPTY" : (f[1] ? "UNPAID" : "PAID");
                    return MyTableResponse.from(op, status);
                })
                .toList();
    }

    /** Per-table takings (card/cash paid + tips, and unpaid) for the current user's tables. */
    @Transactional(readOnly = true)
    public List<TableStatsResponse> myStats() {
        List<OrderPointEntity> ops = assignedOrderPoints(currentUser.require());
        if (ops.isEmpty()) {
            return List.of();
        }
        List<UUID> opIds = ops.stream().map(OrderPointEntity::getId).toList();

        // ordered total per order point
        List<OrderEntity> orders = orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(opIds);
        Map<UUID, UUID> orderToOp = new HashMap<>();
        for (OrderEntity o : orders) {
            orderToOp.put(o.getId(), o.getOrderPointId());
        }
        // per order point: settled (paid lines) and unpaid line totals
        Map<UUID, BigDecimal> unpaidByOp = new HashMap<>();
        Map<UUID, BigDecimal> settledByOp = new HashMap<>();
        if (!orders.isEmpty()) {
            for (OrderItemEntity it : orderItemRepository.findByOrderIdIn(
                    orders.stream().map(OrderEntity::getId).toList())) {
                UUID opId = orderToOp.get(it.getOrderId());
                if (opId == null) {
                    continue;
                }
                BigDecimal price = it.getPrice() == null ? BigDecimal.ZERO : it.getPrice();
                BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(it.getQuantity()));
                if (it.getPaymentId() == null) {
                    unpaidByOp.merge(opId, lineTotal, BigDecimal::add);
                } else {
                    settledByOp.merge(opId, lineTotal, BigDecimal::add);
                }
            }
        }

        // payments grouped by order point
        Map<UUID, List<PaymentEntity>> paymentsByOp = new HashMap<>();
        for (PaymentEntity p : paymentRepository.findByOrderPointIdIn(opIds)) {
            paymentsByOp.computeIfAbsent(p.getOrderPointId(), k -> new ArrayList<>()).add(p);
        }

        List<TableStatsResponse> result = new ArrayList<>(ops.size());
        for (OrderPointEntity op : ops) {
            BigDecimal paidCard = BigDecimal.ZERO;
            BigDecimal paidCash = BigDecimal.ZERO;
            BigDecimal tipCard = BigDecimal.ZERO;
            BigDecimal tipCash = BigDecimal.ZERO;
            for (PaymentEntity p : paymentsByOp.getOrDefault(op.getId(), List.of())) {
                BigDecimal amt = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
                BigDecimal tip = p.getTip() == null ? BigDecimal.ZERO : p.getTip();
                if (p.getMethod() == PaymentMethod.CARD) {
                    paidCard = paidCard.add(amt);
                    tipCard = tipCard.add(tip);
                } else if (p.getMethod() == PaymentMethod.CASH) {
                    paidCash = paidCash.add(amt);
                    tipCash = tipCash.add(tip);
                }
            }
            BigDecimal unpaid = unpaidByOp.getOrDefault(op.getId(), BigDecimal.ZERO);
            BigDecimal settled = settledByOp.getOrDefault(op.getId(), BigDecimal.ZERO);
            result.add(new TableStatsResponse(
                    op.getId(), op.getName(), paidCard, paidCash, tipCard, tipCash,
                    unpaid, settled, op.isProtocol()));
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
        Map<UUID, String> names = new HashMap<>();
        for (OrderPointEntity op : ops) {
            names.put(op.getId(), op.getName());
        }
        return paymentRepository
                .findByOrderPointIdIn(ops.stream().map(OrderPointEntity::getId).toList()).stream()
                .filter(p -> p.getMethod() != PaymentMethod.PROTOCOL)
                .sorted(Comparator.comparing(PaymentEntity::getCreatedAt).reversed())
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
        Map<UUID, String> names = new HashMap<>();
        for (OrderPointEntity op : sources) {
            names.put(op.getId(), op.getName());
        }

        List<OrderEntity> orders = orderRepository
                .findByOrderPointIdInOrderByCreatedAtDesc(new ArrayList<>(names.keySet())).stream()
                .filter(o -> OrderService.BOARD_STATUSES.contains(o.getStatus()))
                .toList();
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        return orders.stream()
                .map(o -> OrderResponse.from(
                        o,
                        itemsByOrder.getOrDefault(o.getId(), List.of()),
                        names.get(o.getOrderPointId())))
                .toList();
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
        Map<UUID, String> names = new HashMap<>();
        for (OrderPointEntity op : ops) {
            names.put(op.getId(), op.getName());
        }
        List<OrderEntity> orders = orderRepository
                .findByOrderPointIdInOrderByCreatedAtDesc(new ArrayList<>(names.keySet())).stream()
                .filter(o -> statuses.contains(o.getStatus()))
                .toList();
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        return orders.stream()
                .map(o -> OrderResponse.from(
                        o,
                        itemsByOrder.getOrDefault(o.getId(), List.of()),
                        names.get(o.getOrderPointId())))
                .toList();
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
                List<OrderPointEntity> candidates = eventId != null
                        ? orderPointRepository.findByLocationIdAndEventIdOrderByName(locationId, eventId)
                        : orderPointRepository.findByLocationIdOrderByName(locationId);
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
            for (UUID id : userIds) {
                if (!valid.contains(id)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "User does not belong to this location's client: " + id);
                }
            }
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

        for (var e : existing) {
            if (!target.contains(e.getUserId())) {
                assignmentRepository.delete(e);
            }
        }
        for (UUID id : target) {
            if (!current.contains(id)) {
                OrderPointAssignmentEntity e = new OrderPointAssignmentEntity();
                e.setLocationId(request.locationId());
                e.setEventId(eventId);
                e.setParentName(parent);
                e.setUserId(id);
                assignmentRepository.save(e);
            }
        }
        return new ParentAssignmentResponse(parent, userIds);
    }

    private LocationEntity requireAccessibleLocation(UUID locationId) {
        UserPrincipal me = currentUser.require();
        LocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Location not found: " + locationId));
        if (!me.isSuper() && !Objects.equals(location.getClientId(), me.clientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found: " + locationId);
        }
        return location;
    }
}
