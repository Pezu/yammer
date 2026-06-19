package com.yammer.controller;

import com.yammer.dto.CustomerOrderPointResponse;
import com.yammer.dto.CustomerOrderRequest;
import com.yammer.dto.MenuItemNode;
import com.yammer.entity.EventEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.EventRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.service.MenuService;
import com.yammer.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unauthenticated, customer-facing endpoints (reached by scanning an order point's QR). Kept under
 * {@code /public/**}, which {@code SecurityConfig} permits without a token.
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final OrderPointRepository orderPointRepository;
    private final EventRepository eventRepository;
    private final MenuService menuService;
    private final OrderService orderService;

    /** The order point a customer landed on: its event and its default menu (both from the order point). */
    @GetMapping("/order-points/{opId}")
    public CustomerOrderPointResponse orderPoint(@PathVariable UUID opId) {
        OrderPointEntity op = orderPointRepository.findById(opId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        String eventName = op.getEventId() == null ? null
                : eventRepository.findById(op.getEventId()).map(EventEntity::getName).orElse(null);
        List<MenuItemNode> menu = op.getMenuId() == null
                ? List.of()
                : menuService.getTreeUnchecked(op.getMenuId());
        return new CustomerOrderPointResponse(op.getId(), op.getName(), op.getEventId(), eventName, menu);
    }

    /** Place a self-service customer order at this order point (pay-later). */
    @PostMapping("/order-points/{opId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public void placeOrder(@PathVariable UUID opId, @Valid @RequestBody CustomerOrderRequest request) {
        orderService.placeCustomerOrder(opId, request);
    }
}
