package com.yammer.service;

import com.yammer.dto.CustomerOrderRequest;
import com.yammer.dto.OrderFilterOptions;
import com.yammer.dto.OrderItemRequest;
import com.yammer.dto.OrderPointBillLine;
import com.yammer.dto.OrderItemsUpdateRequest;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.PagedResponse;
import com.yammer.dto.PlaceOrderRequest;
import com.yammer.dto.ProductReportRow;
import com.yammer.dto.PublicOrderResponse;
import com.yammer.event.OrderChangedEvent;
import com.yammer.event.PaymentCommittedEvent;
import com.yammer.entity.FiscalStatus;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.MenuItemEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final MenuItemRepository menuItemRepository;
    private final CurrentUserProvider currentUser;
    private final AccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderResponseAssembler orderResponseAssembler;
    private final BridgeService bridgeService;

    /**
     * Places an order at an order point with the given line items (name/price snapshotted).
     *
     * <p>When {@code request.paymentMethod()} is present (the non-pay-later / immediate POS flow) the
     * order is created already DELIVERED and settled in full with that method. Otherwise it is created
     * ORDERED for the pay-later kanban flow.
     */
    public OrderResponse place(PlaceOrderRequest request) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(request.orderPointId());
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound(request.orderPointId()));
        boolean immediate = request.paymentMethod() != null;

        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderRepository.maxOrderNoForClient(location.getClientId()) + 1);
        order.setOrderPointId(op.getId());
        order.setEventId(op.getEventId());
        order.setCreatedBy(me.username());
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(immediate ? "DELIVERED" : "ORDERED");
        OrderEntity savedOrder = orderRepository.save(order);

        List<OrderItemEntity> toSave = new ArrayList<>(request.items().size());
        for (OrderItemRequest i : request.items()) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderId(savedOrder.getId());
            item.setMenuItemId(i.menuItemId());
            item.setName(i.name().trim());
            item.setPrice(i.price());
            item.setQuantity(i.quantity());
            toSave.add(item);
        }
        List<OrderItemEntity> savedItems = orderItemRepository.saveAll(toSave);

        if (immediate) {
            settleImmediately(savedItems, op, request.paymentMethod(), request.tip(), me);
        } else {
            eventPublisher.publishEvent(new OrderChangedEvent(savedOrder, "ORDER_CREATED"));
        }
        return OrderResponse.from(savedOrder, savedItems, op.getName());
    }

    /** A validated, price-snapshotted customer order line (resolved from the order point's menu). */
    public record ResolvedLine(UUID menuItemId, String name, BigDecimal price, int quantity) {}

    /** Ids of the order + payment created when an online payment is confirmed. */
    public record OnlineOrderResult(UUID orderId, UUID paymentId) {}

    /**
     * Validates customer cart lines against the order point's default menu and snapshots name+price.
     * The client only sends product ids + quantities, so prices can't be tampered with. Each id must
     * be an orderable product of that menu.
     */
    public List<ResolvedLine> resolveCustomerOrderLines(
            OrderPointEntity op, List<CustomerOrderRequest.Line> lines) {
        if (op.getMenuId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This table has no menu");
        }
        List<UUID> menuItemIds = lines.stream().map(CustomerOrderRequest.Line::menuItemId).toList();
        Map<UUID, MenuItemEntity> menuItems = menuItemRepository.findAllById(menuItemIds).stream()
                .collect(Collectors.toMap(MenuItemEntity::getId, mi -> mi));
        List<ResolvedLine> out = new ArrayList<>(lines.size());
        for (CustomerOrderRequest.Line line : lines) {
            MenuItemEntity mi = menuItems.get(line.menuItemId());
            if (mi == null || !mi.isOrderable() || !op.getMenuId().equals(mi.getMenuId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Item not on this table's menu: " + line.menuItemId());
            }
            out.add(new ResolvedLine(mi.getId(), mi.getName(), mi.getPrice(), line.quantity()));
        }
        return out;
    }

    /** Sum of price × quantity over resolved lines, scaled to 2 decimals. */
    public BigDecimal totalOf(List<ResolvedLine> lines) {
        return lines.stream()
                .map(l -> (l.price() == null ? BigDecimal.ZERO : l.price())
                        .multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Places a self-service customer order (from the public QR page) at an order point: pay-later,
     * created as ORDERED. Returns the new order id.
     */
    public UUID placeCustomerOrder(UUID orderPointId, CustomerOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is empty");
        }
        OrderPointEntity op = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> notFound(orderPointId));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound(orderPointId));
        List<ResolvedLine> lines = resolveCustomerOrderLines(op, request.items());

        OrderEntity savedOrder = saveOrderWithItems(op, location, lines, "ORDERED", null, null);
        eventPublisher.publishEvent(new OrderChangedEvent(savedOrder, "ORDER_CREATED"));
        return savedOrder.getId();
    }

    /**
     * Creates a fully-paid online order: an ORDERED order with its items, an ONLINE
     * {@link PaymentEntity} that settles every line, then fires the board + fiscal events. This is
     * the confirmed-IPN counterpart of {@link #placeCustomerOrder} for pay-now order points.
     */
    public OnlineOrderResult createPaidOnlineOrder(
            OrderPointEntity op, LocationEntity location, List<ResolvedLine> lines,
            String ntpId, UUID customerId) {
        BigDecimal amount = totalOf(lines);

        PaymentEntity payment = new PaymentEntity();
        payment.setOrderPointId(op.getId());
        payment.setEventId(op.getEventId());
        payment.setAmount(amount);
        payment.setTip(BigDecimal.ZERO);
        payment.setMethod(PaymentMethod.ONLINE);
        payment.setCreatedBy("Customer");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setExternalRef(ntpId);
        PaymentEntity savedPayment = paymentRepository.save(payment);

        OrderEntity savedOrder =
                saveOrderWithItems(op, location, lines, "ORDERED", savedPayment.getId(), customerId);

        eventPublisher.publishEvent(new OrderChangedEvent(savedOrder, "ORDER_CREATED"));
        // fiscalize via the durable outbox once this transaction commits (same as a card payment)
        eventPublisher.publishEvent(new PaymentCommittedEvent(savedPayment.getId()));
        return new OnlineOrderResult(savedOrder.getId(), savedPayment.getId());
    }

    /** A customer's order history at one event, newest first (public QR order-history view). */
    @Transactional(readOnly = true)
    public List<PublicOrderResponse> customerOrders(UUID customerId, UUID eventId) {
        if (customerId == null || eventId == null) {
            return List.of();
        }
        List<OrderEntity> orders =
                orderRepository.findByCustomerIdAndEventIdOrderByCreatedAtDesc(customerId, eventId);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        // Group by status (READY → ORDERED → DELIVERED → other), newest first within each.
        // The source list is already createdAt-desc and the sort is stable, so time order holds.
        return orders.stream()
                .sorted(Comparator.comparingInt(o -> customerOrderStatusRank(o.getStatus())))
                .map(o -> {
            List<OrderItemEntity> items = itemsByOrder.getOrDefault(o.getId(), List.of());
            BigDecimal total = items.stream()
                    .map(i -> (i.getPrice() == null ? BigDecimal.ZERO : i.getPrice())
                            .multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            List<PublicOrderResponse.Item> lines = items.stream()
                    .map(i -> new PublicOrderResponse.Item(i.getName(), i.getQuantity(), i.getPrice()))
                    .toList();
            return new PublicOrderResponse(
                    o.getId(), o.getOrderNo(), o.getStatus(),
                    o.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(), total, lines);
        }).toList();
    }

    /** Display order for the customer order history: READY, then ORDERED, then DELIVERED, then rest. */
    private static int customerOrderStatusRank(String status) {
        return switch (status == null ? "" : status) {
            case "READY" -> 0;
            case "ORDERED" -> 1;
            case "DELIVERED" -> 2;
            default -> 3; // CANCELED / unknown last
        };
    }

    /**
     * Persists an order and its line items. When {@code paymentId} is non-null every line is stamped
     * with it (fully settled); otherwise lines are left unpaid. {@code customerId} attributes the
     * order to a customer (self-service pay-now) or null.
     */
    private OrderEntity saveOrderWithItems(
            OrderPointEntity op, LocationEntity location, List<ResolvedLine> lines,
            String status, UUID paymentId, UUID customerId) {
        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderRepository.maxOrderNoForClient(location.getClientId()) + 1);
        order.setOrderPointId(op.getId());
        order.setEventId(op.getEventId());
        order.setCustomerId(customerId);
        order.setCreatedBy("Customer");
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(status);
        OrderEntity savedOrder = orderRepository.save(order);

        List<OrderItemEntity> toSave = new ArrayList<>(lines.size());
        for (ResolvedLine l : lines) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderId(savedOrder.getId());
            item.setMenuItemId(l.menuItemId());
            item.setName(l.name());
            item.setPrice(l.price());
            item.setQuantity(l.quantity());
            item.setPaymentId(paymentId);
            toSave.add(item);
        }
        orderItemRepository.saveAll(toSave);
        return savedOrder;
    }

    /**
     * Settles a just-placed order in full: creates a {@link PaymentEntity}, stamps every line with it,
     * and (for non-protocol) fires the fiscal outbox event — mirroring the line-payment flow but scoped
     * to this single order's lines.
     */
    private void settleImmediately(
            List<OrderItemEntity> items, OrderPointEntity op, PaymentMethod method, BigDecimal tip, UserPrincipal me) {
        boolean protocol = method == PaymentMethod.PROTOCOL;
        BigDecimal amount = items.stream()
                .map(it -> (it.getPrice() == null ? BigDecimal.ZERO : it.getPrice())
                        .multiply(BigDecimal.valueOf(it.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tipAmount = (tip == null ? BigDecimal.ZERO : tip)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        PaymentEntity payment = new PaymentEntity();
        payment.setOrderPointId(op.getId());
        payment.setEventId(op.getEventId());
        payment.setAmount(protocol ? BigDecimal.ZERO : amount);
        payment.setTip(protocol ? BigDecimal.ZERO : tipAmount);
        payment.setMethod(method);
        if (protocol) {
            payment.setFiscalStatus(FiscalStatus.PROTOCOL);
        }
        payment.setCreatedBy(me.username());
        payment.setCreatedAt(LocalDateTime.now());
        PaymentEntity savedPayment = paymentRepository.save(payment);

        items.forEach(it -> it.setPaymentId(savedPayment.getId()));
        orderItemRepository.saveAll(items);

        if (!protocol) {
            // fiscalize via the durable outbox once this transaction commits
            eventPublisher.publishEvent(new PaymentCommittedEvent(savedPayment.getId()));
        }
    }

    /** All orders the caller can see: SUPER → everything; otherwise their own client's. */
    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return toResponses(scopedOrders());
    }

    /** Undelivered, non-cancelled statuses (ORDERED / READY). */
    public static final Set<String> BOARD_STATUSES = Set.of("ORDERED", "READY");
    private static final Set<String> SETTABLE_STATUSES = Set.of("ORDERED", "READY", "DELIVERED", "CANCELED");

    /** Moves an order to a new status (ORDERED → READY → DELIVERED, CANCELED, or back). */
    public OrderResponse updateStatus(UUID orderId, String status) {
        String next = status == null ? "" : status.trim().toUpperCase();
        if (!SETTABLE_STATUSES.contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }
        OrderEntity order = accessGuard.requireAccessibleOrder(orderId);
        OrderPointEntity op = orderPointRepository.findById(order.getOrderPointId())
                .orElseThrow(() -> notFound(order.getOrderPointId()));
        if ("DELIVERED".equals(next) && !"READY".equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only READY orders can be delivered");
        }
        order.setStatus(next);
        OrderEntity saved = orderRepository.save(order);
        if ("DELIVERED".equals(next)) {
            eventPublisher.publishEvent(new OrderChangedEvent(saved, "ORDER_DELIVERED"));
        } else if ("READY".equals(next)) {
            // notify the waiter who placed it (Web Push to the waiter PWA)
            eventPublisher.publishEvent(new OrderChangedEvent(saved, "ORDER_READY"));
        }
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdIn(List.of(saved.getId()));
        return OrderResponse.from(saved, items, op.getName());
    }

    /** Update quantities of an order's unpaid items; quantity ≤ 0 deletes the item. */
    public OrderResponse updateItems(UUID orderId, List<OrderItemsUpdateRequest.ItemQuantity> updates) {
        OrderEntity order = accessGuard.requireAccessibleOrder(orderId);
        OrderPointEntity op = orderPointRepository.findById(order.getOrderPointId())
                .orElseThrow(() -> notFound(order.getOrderPointId()));

        Map<UUID, Integer> qtyById = new HashMap<>();
        if (updates != null) {
            for (OrderItemsUpdateRequest.ItemQuantity u : updates) {
                qtyById.put(u.id(), u.quantity());
            }
        }
        for (OrderItemEntity item : orderItemRepository.findByOrderIdIn(List.of(orderId))) {
            if (item.getPaymentId() != null || !qtyById.containsKey(item.getId())) {
                continue; // paid items and untouched items are left as-is
            }
            int q = qtyById.get(item.getId());
            if (q <= 0) {
                orderItemRepository.delete(item);
            } else if (q != item.getQuantity()) {
                item.setQuantity(q);
                orderItemRepository.save(item);
            }
        }
        List<OrderItemEntity> remaining = orderItemRepository.findByOrderIdIn(List.of(orderId));
        return OrderResponse.from(order, remaining, op.getName());
    }

    /** Completely deletes an order (and its items). Only allowed when nothing is paid. */
    public void deleteOrder(UUID orderId) {
        accessGuard.requireAccessibleOrder(orderId);
        boolean anyPaid = orderItemRepository.findByOrderIdIn(List.of(orderId)).stream()
                .anyMatch(i -> i.getPaymentId() != null);
        if (anyPaid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot delete an order that has paid items");
        }
        orderRepository.deleteById(orderId); // FK cascade removes order_item rows
    }

    /** Every product the caller can see, with the total ordered quantity (highest first). */
    @Transactional(readOnly = true)
    public List<ProductReportRow> productReport(UUID eventId) {
        List<UUID> opIds = accessGuard.visibleOrderPointIds();
        // Aggregated in the database — no full order_item load into the JVM.
        List<OrderItemRepository.ProductQuantity> rows;
        if (eventId != null) {
            // tenant gate: the caller must see at least one of the event's order points
            Set<UUID> eventOps = Set.copyOf(orderPointRepository.findIdsByEventId(eventId));
            if (opIds.stream().noneMatch(eventOps::contains)) {
                return List.of();
            }
            rows = orderItemRepository.aggregateProductQuantitiesByEvent(eventId);
        } else {
            if (opIds.isEmpty()) {
                return List.of();
            }
            rows = orderItemRepository.aggregateProductQuantities(opIds);
        }
        return rows.stream()
                .map(q -> new ProductReportRow(q.getName(), q.getQuantity()))
                .toList();
    }

    /**
     * One page of the caller's visible orders, newest first — server-side paginated and filtered.
     * Every filter is optional: {@code eventId}, {@code orderNo}, {@code orderPointId},
     * {@code waiter} (created_by), {@code status}, and {@code paid} (NOT / PAR / PAID, derived from
     * the order's line payment state).
     */
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> listPaged(
            int page, int size, UUID eventId, Long orderNo, UUID orderPointId,
            String waiter, String status, String paid) {
        UserPrincipal me = currentUser.require();
        List<UUID> opIds = me.isSuper() ? null : accessGuard.visibleOrderPointIds();
        if (opIds != null && opIds.isEmpty()) {
            return new PagedResponse<>(List.of(), 0, page, size);
        }
        Specification<OrderEntity> spec =
                ordersFilter(opIds, eventId, orderNo, orderPointId, waiter, status, paid);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderEntity> result = orderRepository.findAll(spec, pageable);
        return new PagedResponse<>(toResponses(result.getContent()), result.getTotalElements(), page, size);
    }

    /** Builds the dynamic filter for the paginated orders list (null {@code opIds} = SUPER, unscoped). */
    private Specification<OrderEntity> ordersFilter(
            List<UUID> opIds, UUID eventId, Long orderNo, UUID orderPointId,
            String waiter, String status, String paid) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (opIds != null) {
                ps.add(root.get("orderPointId").in(opIds));
            }
            if (eventId != null) {
                ps.add(cb.equal(root.get("eventId"), eventId));
            }
            if (orderNo != null) {
                ps.add(cb.equal(root.get("orderNo"), orderNo));
            }
            if (orderPointId != null) {
                ps.add(cb.equal(root.get("orderPointId"), orderPointId));
            }
            if (waiter != null && !waiter.isBlank()) {
                ps.add(cb.equal(root.get("createdBy"), waiter));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (paid != null && !paid.isBlank()) {
                Predicate hasPaid = cb.exists(itemExists(query, cb, root, true));
                Predicate hasUnpaid = cb.exists(itemExists(query, cb, root, false));
                switch (paid) {
                    case "PAID" -> ps.add(cb.and(hasPaid, cb.not(hasUnpaid)));
                    case "PAR" -> ps.add(cb.and(hasPaid, hasUnpaid));
                    case "NOT" -> ps.add(cb.not(hasPaid)); // no paid lines (incl. empty orders)
                    default -> { /* unknown value: no constraint */ }
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** Subquery: this order has a line that is paid ({@code paid=true}) or unpaid ({@code paid=false}). */
    private Subquery<UUID> itemExists(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Root<OrderEntity> order, boolean paid) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<OrderItemEntity> item = sub.from(OrderItemEntity.class);
        Predicate sameOrder = cb.equal(item.get("orderId"), order.get("id"));
        Predicate payState = paid ? cb.isNotNull(item.get("paymentId")) : cb.isNull(item.get("paymentId"));
        return sub.select(item.get("id")).where(cb.and(sameOrder, payState));
    }

    /** Option lists for the orders-report filter combos (order points + waiter names), scoped to the caller. */
    @Transactional(readOnly = true)
    public OrderFilterOptions filterOptions(UUID eventId) {
        UserPrincipal me = currentUser.require();
        List<UUID> opIds = me.isSuper() ? null : accessGuard.visibleOrderPointIds();
        if (opIds != null && opIds.isEmpty()) {
            return new OrderFilterOptions(List.of(), List.of());
        }

        List<OrderPointEntity> ops;
        if (eventId != null) {
            ops = orderPointRepository.findByEventIdOrderByName(eventId);
            if (opIds != null) {
                Set<UUID> visible = new HashSet<>(opIds);
                ops = ops.stream().filter(o -> visible.contains(o.getId())).toList();
            }
        } else if (opIds != null) {
            ops = orderPointRepository.findAllById(opIds);
        } else {
            ops = orderPointRepository.findAll();
        }
        List<OrderFilterOptions.OrderPointOption> orderPoints = ops.stream()
                .map(o -> new OrderFilterOptions.OrderPointOption(o.getId(), o.getName()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();

        List<String> waiters;
        if (opIds == null) {
            waiters = eventId == null
                    ? orderRepository.distinctWaiters()
                    : orderRepository.distinctWaitersByEvent(eventId);
        } else {
            waiters = eventId == null
                    ? orderRepository.distinctWaitersByOps(opIds)
                    : orderRepository.distinctWaitersByOpsAndEvent(eventId, opIds);
        }

        return new OrderFilterOptions(orderPoints, waiters);
    }

    /** Orders visible to the current user: SUPER → everything; otherwise their own client's. */
    private List<OrderEntity> scopedOrders() {
        UserPrincipal me = currentUser.require();
        if (me.isSuper()) {
            return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        List<UUID> opIds = accessGuard.visibleOrderPointIds();
        if (opIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(opIds);
    }

    /** Orders placed at one order point (newest first). */
    @Transactional(readOnly = true)
    public List<OrderResponse> listByOrderPoint(UUID orderPointId) {
        accessGuard.requireAccessibleOrderPoint(orderPointId);
        return toResponses(orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(List.of(orderPointId)));
    }

    /**
     * The order point's bill aggregated per product: paid and unpaid quantities. Computed in the DB
     * so the waiter table screen never loads the point's full order/item history.
     */
    @Transactional(readOnly = true)
    public List<OrderPointBillLine> billByOrderPoint(UUID orderPointId) {
        accessGuard.requireAccessibleOrderPoint(orderPointId);
        return orderItemRepository.billByOrderPoint(orderPointId).stream()
                .map(b -> new OrderPointBillLine(
                        b.getMenuItemId(), b.getName(), b.getPrice(), b.getPaidQty(), b.getUnpaidQty()))
                .toList();
    }

    /**
     * Prints the non-fiscal proforma (the current unpaid bill) on the order point's
     * thermal printer via the on-prem bridge.
     */
    public void printProforma(UUID orderPointId) {
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(orderPointId);

        List<OrderEntity> orders = orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(List.of(orderPointId));
        Map<UUID, Long> orderNoById = orders.stream()
                .collect(Collectors.toMap(OrderEntity::getId, OrderEntity::getOrderNo));
        List<OrderItemEntity> unpaid = orders.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList()).stream()
                        .filter(it -> it.getPaymentId() == null)
                        .toList();

        // One pass groups the unpaid lines into InfoLines by product name (first-seen order preserved).
        Map<String, BridgeService.InfoLine> byName = unpaid.stream().collect(Collectors.toMap(
                OrderItemEntity::getName,
                OrderService::toInfoLine,
                (a, b) -> new BridgeService.InfoLine(
                        a.name(), a.quantity() + b.quantity(), a.unitPrice(), a.lineTotal().add(b.lineTotal())),
                LinkedHashMap::new));
        if (byName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to print — no unpaid items");
        }

        List<BridgeService.InfoLine> lines = List.copyOf(byName.values());
        BigDecimal total = lines.stream()
                .map(BridgeService.InfoLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Integer> orderNoList = unpaid.stream()
                .map(it -> orderNoById.get(it.getOrderId()))
                .filter(Objects::nonNull)
                .map(Long::intValue)
                .distinct()
                .sorted()
                .toList();

        String waiter = currentUser.require().username();
        bridgeService.sendInfo(op, waiter, orderNoList, lines, total);
    }

    private static BridgeService.InfoLine toInfoLine(OrderItemEntity it) {
        BigDecimal price = it.getPrice() == null ? BigDecimal.ZERO : it.getPrice();
        return new BridgeService.InfoLine(
                it.getName(), it.getQuantity(), price, price.multiply(BigDecimal.valueOf(it.getQuantity())));
    }

    private List<OrderResponse> toResponses(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<UUID> opIds = orders.stream().map(OrderEntity::getOrderPointId).collect(Collectors.toSet());
        Map<UUID, String> opNames = orderPointRepository.findAllById(opIds).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        return orderResponseAssembler.assemble(orders, opNames);
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found: " + id);
    }
}
