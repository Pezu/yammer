package com.yammer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.config.NetopiaProperties;
import com.yammer.dto.CustomerOrderRequest;
import com.yammer.dto.CustomerOrderResponse;
import com.yammer.dto.OnlinePaymentStatusResponse;
import com.yammer.dto.netopia.NetopiaIpnRequest;
import com.yammer.dto.netopia.NetopiaIpnResponse;
import com.yammer.dto.netopia.NetopiaStartResponse;
import com.yammer.entity.CustomerEntity;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OnlinePaymentEntity;
import com.yammer.entity.OnlinePaymentStatus;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.CustomerRepository;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OnlinePaymentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.service.OrderService.OnlineOrderResult;
import com.yammer.service.OrderService.ResolvedLine;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Drives the online (Netopia) self-service payment flow for pay-now order points. A cart is parked
 * as an {@link OnlinePaymentEntity} (PENDING) while the customer is on the gateway; the real order +
 * payment are created only when the gateway IPN confirms — so no order exists for abandoned/failed
 * payments. Confirmation is driven exclusively by the server-to-server IPN, never the browser return.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    /** Netopia payment status codes that mean "paid". */
    private static final int STATUS_CONFIRMED = 3;
    private static final int STATUS_CONFIRMED_PENDING = 5;
    private static final int STATUS_PENDING = 15;
    /** Abandon PENDING intents the customer never completed after this long. */
    private static final int EXPIRE_AFTER_MINUTES = 30;

    private final OnlinePaymentRepository onlinePaymentRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final NetopiaPaymentService netopiaPaymentService;
    private final NetopiaProperties props;
    private final ObjectMapper objectMapper;

    /**
     * Parks the cart and starts a gateway payment, returning the {@code paymentUrl} to redirect to.
     * No order is created yet.
     */
    @Transactional
    public CustomerOrderResponse start(UUID orderPointId, CustomerOrderRequest request) {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Online payment is not available");
        }
        if (request.returnUrl() == null || request.returnUrl().isBlank()
                || !request.returnUrl().startsWith("http")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing return URL");
        }
        OrderPointEntity op = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));

        List<ResolvedLine> lines = orderService.resolveCustomerOrderLines(op, request.items());
        BigDecimal amount = orderService.totalOf(lines);

        // Resolve the customer (returning → by id; first-time → create/reuse by phone) for billing.
        CustomerEntity customer = resolveCustomer(request.customer());

        OnlinePaymentEntity intent = new OnlinePaymentEntity();
        intent.setOrderPointId(op.getId());
        intent.setEventId(op.getEventId());
        intent.setCustomerId(customer != null ? customer.getId() : null);
        intent.setAmount(amount);
        intent.setItems(writeItems(lines));
        intent.setStatus(OnlinePaymentStatus.PENDING);
        intent.setCreatedAt(LocalDateTime.now());
        OnlinePaymentEntity saved = onlinePaymentRepository.save(intent);

        String firstName = customer != null ? customer.getFirstName() : "Guest";
        String lastName = customer != null ? customer.getLastName() : "Customer";
        String email = customer == null ? null : customer.getEmail();
        String phone = customer == null ? null
                : ((customer.getPrefix() == null ? "" : customer.getPrefix())
                        + (customer.getPhone() == null ? "" : customer.getPhone()));
        String redirectUrl = appendRef(request.returnUrl(), saved.getId());
        NetopiaStartResponse response = netopiaPaymentService.startPayment(
                saved.getId().toString(), amount.doubleValue(), firstName, lastName, email, phone, redirectUrl);
        String paymentUrl = response == null || response.payment() == null
                ? null : response.payment().paymentURL();
        if (paymentUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not start payment");
        }
        if (response.payment().ntpID() != null) {
            saved.setNtpId(response.payment().ntpID());
            saved.setUpdatedAt(LocalDateTime.now());
        }
        return CustomerOrderResponse.redirect(
                saved.getId(), paymentUrl, customer != null ? customer.getId() : null);
    }

    /**
     * Resolves the customer for a pay-now order: by id when a returning customer is known, otherwise
     * create or reuse one keyed by phone from the supplied name. Returns null when no identity was
     * provided (anonymous → generic billing name).
     */
    private CustomerEntity resolveCustomer(CustomerOrderRequest.Customer c) {
        if (c == null) {
            return null;
        }
        if (c.id() != null) {
            return customerRepository.findById(c.id()).orElse(null);
        }
        String first = c.firstName() == null ? "" : c.firstName().trim();
        String last = c.lastName() == null ? "" : c.lastName().trim();
        String email = c.email() == null ? "" : c.email().trim();
        String prefix = c.prefix() == null ? "" : c.prefix().trim();
        String phone = c.phone() == null ? "" : c.phone().trim();
        if (first.isEmpty() && last.isEmpty()) {
            return null;
        }
        if (!phone.isEmpty()) {
            CustomerEntity existing = customerRepository.findFirstByPrefixAndPhone(prefix, phone).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        CustomerEntity created = new CustomerEntity();
        created.setFirstName(first.isEmpty() ? "Guest" : first);
        created.setLastName(last.isEmpty() ? "Customer" : last);
        created.setEmail(email.isEmpty() ? null : email);
        created.setPrefix(prefix.isEmpty() ? null : prefix);
        created.setPhone(phone.isEmpty() ? null : phone);
        return customerRepository.save(created);
    }

    /** Look up a customer id by prefix + phone for the pay-now flow; null when not found. */
    @Transactional(readOnly = true)
    public UUID lookupCustomerId(String prefix, String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return customerRepository
                .findFirstByPrefixAndPhone(prefix == null ? "" : prefix.trim(), phone.trim())
                .map(CustomerEntity::getId)
                .orElse(null);
    }

    /**
     * Handles a Netopia IPN — the single source of truth for whether a payment completed. Validates
     * the intent and amount, then on a paid status creates the order + payment (idempotently).
     */
    @Transactional
    public NetopiaIpnResponse handleIpn(NetopiaIpnRequest request) {
        if (request.order() == null || request.payment() == null) {
            return new NetopiaIpnResponse(1, "Invalid request");
        }
        UUID reference;
        try {
            reference = UUID.fromString(request.order().orderID());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("IPN with unparseable orderID: {}", request.order().orderID());
            return new NetopiaIpnResponse(0, "Unknown reference");
        }
        OnlinePaymentEntity intent = onlinePaymentRepository.findById(reference).orElse(null);
        if (intent == null) {
            log.warn("IPN for unknown online payment: {}", reference);
            return new NetopiaIpnResponse(0, "Unknown reference");
        }
        if (intent.getStatus() != OnlinePaymentStatus.PENDING) {
            // already handled — idempotent ack (Netopia may retry IPNs)
            return new NetopiaIpnResponse(0, "Already processed");
        }

        Integer status = request.payment().status();
        // Netopia's IPN doesn't always echo the amount; only reject when it does AND it mismatches.
        // (The amount Netopia charged is the one we sent at start, so absence is not a failure.)
        Double paidAmount = request.order().amount();
        if (paidAmount != null && Math.abs(paidAmount - intent.getAmount().doubleValue()) > 0.01) {
            log.warn("IPN amount mismatch for {}: paid={}, expected={}",
                    reference, paidAmount, intent.getAmount());
            markFailed(intent);
            return new NetopiaIpnResponse(0, "Amount mismatch");
        }

        try {
            if (status != null && (status == STATUS_CONFIRMED || status == STATUS_CONFIRMED_PENDING)) {
                confirm(intent, request.order().ntpID());
                return new NetopiaIpnResponse(0, "Payment confirmed");
            } else if (status != null && status == STATUS_PENDING) {
                return new NetopiaIpnResponse(0, "Payment pending");
            } else {
                log.info("IPN reports non-paid status {} for {}", status, reference);
                markFailed(intent);
                return new NetopiaIpnResponse(0, "Payment failed");
            }
        } catch (Exception e) {
            log.error("Error processing IPN for {}", reference, e);
            return new NetopiaIpnResponse(1, "Processing error");
        }
    }

    /** Status of an intent, for the customer return page. */
    @Transactional(readOnly = true)
    public OnlinePaymentStatusResponse status(UUID reference) {
        OnlinePaymentEntity intent = onlinePaymentRepository.findById(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return new OnlinePaymentStatusResponse(intent.getStatus().name(), intent.getOrderId());
    }

    private void confirm(OnlinePaymentEntity intent, String ntpId) {
        OrderPointEntity op = orderPointRepository.findById(intent.getOrderPointId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        List<ResolvedLine> lines = readItems(intent.getItems());

        OnlineOrderResult result = orderService.createPaidOnlineOrder(
                op, location, lines, ntpId != null ? ntpId : intent.getNtpId(), intent.getCustomerId());

        intent.setStatus(OnlinePaymentStatus.PAID);
        intent.setOrderId(result.orderId());
        intent.setPaymentId(result.paymentId());
        if (ntpId != null) {
            intent.setNtpId(ntpId);
        }
        intent.setUpdatedAt(LocalDateTime.now());
        onlinePaymentRepository.save(intent);
        log.info("Online payment {} confirmed -> order {}", intent.getId(), result.orderId());
    }

    private void markFailed(OnlinePaymentEntity intent) {
        intent.setStatus(OnlinePaymentStatus.FAILED);
        intent.setUpdatedAt(LocalDateTime.now());
        onlinePaymentRepository.save(intent);
    }

    /** Expires PENDING intents the customer abandoned (no IPN ever arrived). */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    @Transactional
    public void expireStale() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRE_AFTER_MINUTES);
        List<OnlinePaymentEntity> stale =
                onlinePaymentRepository.findByStatusAndCreatedAtBefore(OnlinePaymentStatus.PENDING, cutoff);
        for (OnlinePaymentEntity intent : stale) {
            intent.setStatus(OnlinePaymentStatus.EXPIRED);
            intent.setUpdatedAt(LocalDateTime.now());
        }
        if (!stale.isEmpty()) {
            onlinePaymentRepository.saveAll(stale);
            log.info("Expired {} stale online payment(s)", stale.size());
        }
    }

    private String appendRef(String returnUrl, UUID reference) {
        return returnUrl + (returnUrl.contains("?") ? "&" : "?") + "ref=" + reference;
    }

    private String writeItems(List<ResolvedLine> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize order items", e);
        }
    }

    private List<ResolvedLine> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ResolvedLine>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read order items", e);
        }
    }
}
