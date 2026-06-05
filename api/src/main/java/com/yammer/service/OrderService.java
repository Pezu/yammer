package com.yammer.service;

import com.yammer.dto.OrderItemRequest;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.PlaceOrderRequest;
import com.yammer.dto.ProductReportRow;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final CurrentUserProvider currentUser;
    private final OrderNotificationService notificationService;

    /** Places an order at an order point with the given line items (name/price snapshotted). */
    public OrderResponse place(PlaceOrderRequest request) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = orderPointRepository.findById(request.orderPointId())
                .orElseThrow(() -> notFound(request.orderPointId()));

        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound(request.orderPointId()));
        if (!me.isSuper() && !Objects.equals(location.getClientId(), me.clientId())) {
            throw notFound(request.orderPointId());
        }

        OrderEntity order = new OrderEntity();
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
        notificationService.orderCreated(savedOrder);
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
        UserPrincipal me = currentUser.require();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        OrderPointEntity op = orderPointRepository.findById(order.getOrderPointId())
                .orElseThrow(() -> notFound(order.getOrderPointId()));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound(order.getOrderPointId()));
        if (!me.isSuper() && !Objects.equals(location.getClientId(), me.clientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId);
        }
        if ("DELIVERED".equals(next) && !"READY".equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only READY orders can be delivered");
        }
        order.setStatus(next);
        OrderEntity saved = orderRepository.save(order);
        if ("DELIVERED".equals(next)) {
            notificationService.orderDelivered(saved);
        }
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdIn(List.of(saved.getId()));
        return OrderResponse.from(saved, items, op.getName());
    }

    /** Every product the caller can see, with the total ordered quantity (highest first). */
    @Transactional(readOnly = true)
    public List<ProductReportRow> productReport() {
        List<OrderEntity> orders = scopedOrders();
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<String, Long> qtyByName = new LinkedHashMap<>();
        orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
                .forEach(i -> qtyByName.merge(i.getName(), (long) i.getQuantity(), Long::sum));
        return qtyByName.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new ProductReportRow(e.getKey(), e.getValue()))
                .toList();
    }

    /** Orders visible to the current user: SUPER → everything; otherwise their own client's. */
    private List<OrderEntity> scopedOrders() {
        UserPrincipal me = currentUser.require();
        if (me.isSuper()) {
            return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
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
        List<UUID> opIds = orderPointRepository.findByLocationIdIn(locationIds).stream()
                .map(OrderPointEntity::getId)
                .toList();
        if (opIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(opIds);
    }

    /** Orders placed at one order point (newest first). */
    @Transactional(readOnly = true)
    public List<OrderResponse> listByOrderPoint(UUID orderPointId) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> notFound(orderPointId));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound(orderPointId));
        if (!me.isSuper() && !Objects.equals(location.getClientId(), me.clientId())) {
            throw notFound(orderPointId);
        }
        return toResponses(orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(List.of(orderPointId)));
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
