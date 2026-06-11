package com.yammer.service;

import com.yammer.dto.OrderItemRequest;
import com.yammer.dto.OrderItemsUpdateRequest;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.PagedResponse;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final CurrentUserProvider currentUser;
    private final AccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderResponseAssembler orderResponseAssembler;
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

    /** One page of the caller's visible orders, newest first — server-side paginated. */
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> listPaged(int page, int size) {
        UserPrincipal me = currentUser.require();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderEntity> result;
        if (me.isSuper()) {
            result = orderRepository.findAll(pageable);
        } else {
            List<UUID> opIds = accessGuard.visibleOrderPointIds();
            if (opIds.isEmpty()) {
                return new PagedResponse<>(List.of(), 0, page, size);
            }
            result = orderRepository.findByOrderPointIdIn(opIds, pageable);
        }
        return new PagedResponse<>(toResponses(result.getContent()), result.getTotalElements(), page, size);
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

        bridgeService.sendInfo(op, orderNoList, lines, total);
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
