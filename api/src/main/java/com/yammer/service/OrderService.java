package com.yammer.service;

import com.yammer.dto.OrderItemRequest;
import com.yammer.dto.OrderItemsUpdateRequest;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.PlaceOrderRequest;
import com.yammer.dto.ProductReportRow;
import com.yammer.event.OrderChangedEvent;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
@lombok.extern.slf4j.Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final CurrentUserProvider currentUser;
    private final AccessGuard accessGuard;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final BridgeService bridgeService;

    /** Places an order at an order point with the given line items (name/price snapshotted). */
    public OrderResponse place(PlaceOrderRequest request) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(request.orderPointId());
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound(request.orderPointId()));

        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderRepository.maxOrderNoForClient(location.getClientId()) + 1);
        order.setOrderPointId(op.getId());
        order.setCreatedBy(me.username());
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("ORDERED");
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
        eventPublisher.publishEvent(new OrderChangedEvent(savedOrder, "ORDER_CREATED"));
        return OrderResponse.from(savedOrder, savedItems, op.getName());
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
        }
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdIn(List.of(saved.getId()));
        return OrderResponse.from(saved, items, op.getName());
    }

    /** Update quantities of an order's unpaid items; quantity ≤ 0 deletes the item. */
    public OrderResponse updateItems(UUID orderId, List<OrderItemsUpdateRequest.ItemQuantity> updates) {
        OrderEntity order = requireAccessibleOrder(orderId);
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
        requireAccessibleOrder(orderId);
        boolean anyPaid = orderItemRepository.findByOrderIdIn(List.of(orderId)).stream()
                .anyMatch(i -> i.getPaymentId() != null);
        if (anyPaid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot delete an order that has paid items");
        }
        orderRepository.deleteById(orderId); // FK cascade removes order_item rows
    }

    private OrderEntity requireAccessibleOrder(UUID orderId) {
        return accessGuard.requireAccessibleOrder(orderId);
    }

    /** Every product the caller can see, with the total ordered quantity (highest first). */
    @Transactional(readOnly = true)
    public List<ProductReportRow> productReport() {
        List<UUID> opIds = accessGuard.visibleOrderPointIds();
        if (opIds.isEmpty()) {
            return List.of();
        }
        // Aggregated in the database — no full order_item load into the JVM.
        return orderItemRepository.aggregateProductQuantities(opIds).stream()
                .map(q -> new ProductReportRow(q.getName(), q.getQuantity()))
                .toList();
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
     * Prints the non-fiscal proforma (the current unpaid bill) on the order point's
     * thermal printer via the on-prem bridge.
     */
    public void printProforma(UUID orderPointId) {
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(orderPointId);

        List<OrderEntity> orders = orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(List.of(orderPointId));
        Map<UUID, Long> orderNoById = orders.stream()
                .collect(Collectors.toMap(OrderEntity::getId, OrderEntity::getOrderNo));

        // unpaid lines grouped by product name (preserving first-seen order)
        Map<String, int[]> qtyByName = new java.util.LinkedHashMap<>();
        Map<String, java.math.BigDecimal> priceByName = new java.util.HashMap<>();
        java.util.Set<Long> orderNos = new java.util.TreeSet<>();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        if (!orders.isEmpty()) {
            List<OrderItemEntity> items =
                    orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());
            for (OrderItemEntity it : items) {
                if (it.getPaymentId() != null) {
                    continue; // already settled
                }
                java.math.BigDecimal price = it.getPrice() == null ? java.math.BigDecimal.ZERO : it.getPrice();
                qtyByName.computeIfAbsent(it.getName(), k -> new int[1])[0] += it.getQuantity();
                priceByName.putIfAbsent(it.getName(), price);
                total = total.add(price.multiply(java.math.BigDecimal.valueOf(it.getQuantity())));
                Long no = orderNoById.get(it.getOrderId());
                if (no != null) {
                    orderNos.add(no);
                }
            }
        }

        if (qtyByName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to print — no unpaid items");
        }

        List<BridgeService.InfoLine> lines = qtyByName.entrySet().stream()
                .map(e -> {
                    int qty = e.getValue()[0];
                    java.math.BigDecimal unit = priceByName.get(e.getKey());
                    return new BridgeService.InfoLine(
                            e.getKey(), qty, unit, unit.multiply(java.math.BigDecimal.valueOf(qty)));
                })
                .toList();
        List<Integer> orderNoList = orderNos.stream().map(Long::intValue).toList();

        bridgeService.sendInfo(op, orderNoList, lines, total);
    }

    private List<OrderResponse> toResponses(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));

        Set<UUID> opIds = orders.stream().map(OrderEntity::getOrderPointId).collect(Collectors.toSet());
        Map<UUID, String> opNames = orderPointRepository.findAllById(opIds).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));

        return orders.stream()
                .map(o -> OrderResponse.from(
                        o, itemsByOrder.getOrDefault(o.getId(), List.of()), opNames.get(o.getOrderPointId())))
                .toList();
    }

    private ResponseStatusException notFound(java.util.UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found: " + id);
    }
}
