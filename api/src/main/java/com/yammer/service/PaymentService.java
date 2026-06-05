package com.yammer.service;

import com.yammer.dto.PaymentRequest;
import com.yammer.dto.PaymentResponse;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
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
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrderPoint(UUID orderPointId) {
        requireAccessible(orderPointId);
        return paymentRepository.findByOrderPointIdOrderByCreatedAtDesc(orderPointId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    public PaymentResponse create(PaymentRequest request) {
        UserPrincipal me = requireAccessible(request.orderPointId());

        BigDecimal amount = request.amount().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal tip = (request.tip() == null ? BigDecimal.ZERO : request.tip())
                .max(BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal remaining = remaining(request.orderPointId());
        if (remaining.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing left to pay");
        }
        // small epsilon for rounding
        if (amount.subtract(remaining).compareTo(new BigDecimal("0.01")) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Amount exceeds the remaining balance (" + remaining + ")");
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setOrderPointId(request.orderPointId());
        payment.setAmount(amount);
        payment.setTip(tip);
        payment.setMethod(request.method());
        payment.setCreatedBy(me.username());
        payment.setCreatedAt(LocalDateTime.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    /** ordered total − already-paid amount (tips excluded). */
    private BigDecimal remaining(UUID orderPointId) {
        List<OrderEntity> orders =
                orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(List.of(orderPointId));
        BigDecimal ordered = BigDecimal.ZERO;
        if (!orders.isEmpty()) {
            List<OrderItemEntity> items =
                    orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());
            for (OrderItemEntity i : items) {
                BigDecimal price = i.getPrice() == null ? BigDecimal.ZERO : i.getPrice();
                ordered = ordered.add(price.multiply(BigDecimal.valueOf(i.getQuantity())));
            }
        }
        BigDecimal paid = paymentRepository.findByOrderPointIdOrderByCreatedAtDesc(orderPointId).stream()
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ordered.subtract(paid);
    }

    private UserPrincipal requireAccessible(UUID orderPointId) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order point not found: " + orderPointId));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order point not found: " + orderPointId));
        if (!me.isSuper() && !Objects.equals(location.getClientId(), me.clientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found: " + orderPointId);
        }
        return me;
    }
}
