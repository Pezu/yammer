package com.yammer.controller;

import com.yammer.dto.CustomerOrderPointResponse;
import com.yammer.dto.CustomerOrderRequest;
import com.yammer.dto.CustomerOrderResponse;
import com.yammer.dto.MenuItemNode;
import com.yammer.dto.OnlinePaymentStatusResponse;
import com.yammer.dto.netopia.NetopiaIpnRequest;
import com.yammer.dto.netopia.NetopiaIpnResponse;
import com.yammer.entity.EventEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.EventRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.service.MenuService;
import com.yammer.service.OnlinePaymentService;
import com.yammer.service.OrderService;
import com.yammer.service.StorageService;
import com.yammer.service.StorageService.StoredObject;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unauthenticated, customer-facing endpoints (reached by scanning an order point's QR). Kept under
 * {@code /public/**}, which {@code SecurityConfig} permits without a token.
 */
@Slf4j
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final OrderPointRepository orderPointRepository;
    private final EventRepository eventRepository;
    private final MenuService menuService;
    private final OrderService orderService;
    private final OnlinePaymentService onlinePaymentService;
    private final StorageService storageService;

    /** The order point a customer landed on: its event and its default menu (both from the order point). */
    @GetMapping("/order-points/{opId}")
    public CustomerOrderPointResponse orderPoint(@PathVariable UUID opId) {
        OrderPointEntity op = orderPointRepository.findById(opId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        EventEntity event = op.getEventId() == null ? null : eventRepository.findById(op.getEventId()).orElse(null);
        String eventName = event == null ? null : event.getName();
        UUID clientId = event == null ? null : event.getClientId();
        List<MenuItemNode> menu = op.getMenuId() == null
                ? List.of()
                : menuService.getTreeUnchecked(op.getMenuId());
        return new CustomerOrderPointResponse(
                op.getId(), op.getName(), op.getEventId(), eventName, clientId, menu);
    }

    /**
     * Place a self-service customer order. Pay-later order points create the order immediately;
     * pay-now order points park the cart and return a Netopia {@code paymentUrl} to redirect to (the
     * order is created only once the gateway confirms payment via IPN).
     */
    @PostMapping("/order-points/{opId}/orders")
    public CustomerOrderResponse placeOrder(
            @PathVariable UUID opId, @Valid @RequestBody CustomerOrderRequest request) {
        OrderPointEntity op = orderPointRepository.findById(opId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        if (op.isPayLater()) {
            return CustomerOrderResponse.placed(orderService.placeCustomerOrder(opId, request));
        }
        return onlinePaymentService.start(opId, request);
    }

    /** Status of an online payment intent — polled by the customer return page after the gateway. */
    @GetMapping("/payments/{reference}/status")
    public OnlinePaymentStatusResponse paymentStatus(@PathVariable UUID reference) {
        return onlinePaymentService.status(reference);
    }

    /**
     * Netopia IPN (server-to-server) — the authoritative payment-status callback. Marks the order
     * paid (or failed) here, never on the browser return.
     */
    @PostMapping("/payments/netopia/notify")
    public NetopiaIpnResponse netopiaNotify(
            @RequestBody NetopiaIpnRequest request,
            @RequestHeader(value = "Verification-token", required = false) String verificationToken,
            @RequestHeader MultiValueMap<String, String> headers) {
        // TEMP: log the raw IPN so we can design caller verification (Netopia signs a JWT in the
        // `Verification-token` header — RSA/SHA512, sub = base64(sha512(body)), iss=NETOPIA Payments).
        log.info("Netopia IPN: orderID={} status={} amount={} | Verification-token={} | headers={}",
                request.order() == null ? null : request.order().orderID(),
                request.payment() == null ? null : request.payment().status(),
                request.order() == null ? null : request.order().amount(),
                verificationToken, headers);
        return onlinePaymentService.handleIpn(request);
    }

    /** Serves a menu-item image by its object key (restricted to the menu-items namespace). */
    @GetMapping("/menu-image")
    public ResponseEntity<byte[]> menuImage(@RequestParam String object) {
        if (object == null || !object.startsWith(MenuService.IMAGE_PREFIX + "/")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        StoredObject stored = storageService.get(object)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .body(stored.data());
    }
}
