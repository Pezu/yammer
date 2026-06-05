package com.yammer.service;

import com.yammer.dto.LinePaymentRequest;
import com.yammer.dto.LinePaymentRequest.LinePaymentItem;
import com.yammer.dto.LinePaymentResult;
import com.yammer.dto.LinePaymentResult.Split;
import com.yammer.dto.PaymentMode;
import com.yammer.entity.FiscalStatus;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentMethod;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Settles a table order (= an order point's running bill) at the product level,
 * splitting order lines when a partial quantity is paid.
 *
 * <p><b>Allocation order (deterministic).</b> For a product, unpaid lines are
 * consumed across all the table order's orders ordered by the parent order's
 * {@code created_at} ascending (oldest order first), then by {@code order_item.id}
 * ascending as a stable tie-break ({@code order_item} carries no timestamp).
 *
 * <p><b>Split direction.</b> When a requested quantity is smaller than a line's
 * quantity, the <em>original</em> line stays unpaid with its quantity reduced
 * (same {@code order_id}, {@code payment_id} still NULL); a <em>new</em> line is
 * inserted under the same order for the paid quantity (price/name/product copied,
 * {@code payment_id} = the new payment). Total quantity per product is preserved.
 *
 * <p>The whole operation runs in one transaction; feasibility is validated before
 * any Payment row is created, so a rejection leaves nothing behind.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentSplitService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final CurrentUserProvider currentUser;

    public LinePaymentResult pay(LinePaymentRequest request) {
        UserPrincipal me = requireAccessible(request.orderPointId());

        // Unpaid lines under the table order, in deterministic allocation order.
        List<OrderEntity> orders = orderRepository.findByOrderPointIdOrderByCreatedAtAsc(request.orderPointId());
        Map<UUID, LocalDateTime> createdAt = new LinkedHashMap<>();
        for (OrderEntity o : orders) {
            createdAt.put(o.getId(), o.getCreatedAt());
        }
        List<OrderItemEntity> allItems = orders.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());

        Comparator<OrderItemEntity> allocationOrder =
                Comparator.<OrderItemEntity, LocalDateTime>comparing(i -> createdAt.get(i.getOrderId()))
                        .thenComparing(i -> i.getId().toString());
        List<OrderItemEntity> unpaid = allItems.stream()
                .filter(i -> i.getPaymentId() == null)
                .sorted(allocationOrder)
                .toList();

        BigDecimal tip = (request.tip() == null ? BigDecimal.ZERO : request.tip())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return request.mode() == PaymentMode.FULL
                ? payFull(request, me, unpaid, tip)
                : payPartial(request, me, unpaid, tip);
    }

    // --- full ---

    private LinePaymentResult payFull(
            LinePaymentRequest request, UserPrincipal me, List<OrderItemEntity> unpaid, BigDecimal tip) {
        if (unpaid.isEmpty()) {
            // already fully paid: no-op success, no empty Payment row
            return new LinePaymentResult(null, BigDecimal.ZERO.setScale(2), List.of(), List.of());
        }
        BigDecimal amount = unpaid.stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        PaymentEntity payment = createPayment(request, amount, tip, me);
        List<UUID> covered = new ArrayList<>(unpaid.size());
        for (OrderItemEntity line : unpaid) {
            line.setPaymentId(payment.getId());
            covered.add(line.getId());
        }
        orderItemRepository.saveAll(unpaid);
        return new LinePaymentResult(payment.getId(), amount, covered, List.of());
    }

    // --- partial ---

    private record Alloc(OrderItemEntity line, int coveredQty) {
    }

    private LinePaymentResult payPartial(
            LinePaymentRequest request, UserPrincipal me, List<OrderItemEntity> unpaid, BigDecimal tip) {
        List<LinePaymentItem> items = request.items();
        if (items == null || items.isEmpty()) {
            throw badRequest("Partial payment requires at least one item");
        }

        // Sum duplicate products; reject non-positive quantities.
        Map<UUID, Integer> requested = new LinkedHashMap<>();
        for (LinePaymentItem it : items) {
            if (it.quantity() <= 0) {
                throw badRequest("Quantity must be a positive integer");
            }
            requested.merge(it.menuItemId(), it.quantity(), Integer::sum);
        }

        // Unpaid lines grouped by product, preserving the deterministic order.
        Map<UUID, List<OrderItemEntity>> unpaidByProduct = new LinkedHashMap<>();
        for (OrderItemEntity line : unpaid) {
            unpaidByProduct.computeIfAbsent(line.getMenuItemId(), k -> new ArrayList<>()).add(line);
        }

        // Validate feasibility BEFORE any mutation / Payment creation.
        for (Map.Entry<UUID, Integer> e : requested.entrySet()) {
            List<OrderItemEntity> lines = unpaidByProduct.getOrDefault(e.getKey(), List.of());
            if (lines.isEmpty()) {
                throw badRequest("Product has no unpaid lines in this table order: " + e.getKey());
            }
            int available = lines.stream().mapToInt(OrderItemEntity::getQuantity).sum();
            if (e.getValue() > available) {
                throw badRequest("Requested " + e.getValue() + " exceeds unpaid " + available
                        + " for product " + e.getKey());
            }
        }

        // Plan the allocation (no mutation) and compute the covered amount.
        List<Alloc> plan = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> e : requested.entrySet()) {
            int remaining = e.getValue();
            for (OrderItemEntity line : unpaidByProduct.get(e.getKey())) {
                if (remaining <= 0) {
                    break;
                }
                int take = Math.min(remaining, line.getQuantity());
                plan.add(new Alloc(line, take));
                amount = amount.add(unitPrice(line).multiply(BigDecimal.valueOf(take)));
                remaining -= take;
            }
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        // Apply: create the Payment, then stamp/split lines.
        PaymentEntity payment = createPayment(request, amount, tip, me);
        List<UUID> covered = new ArrayList<>();
        List<Split> splits = new ArrayList<>();
        for (Alloc a : plan) {
            OrderItemEntity line = a.line();
            if (a.coveredQty() == line.getQuantity()) {
                line.setPaymentId(payment.getId());
                orderItemRepository.save(line);
                covered.add(line.getId());
            } else {
                OrderItemEntity paid = new OrderItemEntity();
                paid.setOrderId(line.getOrderId());
                paid.setMenuItemId(line.getMenuItemId());
                paid.setName(line.getName());
                paid.setPrice(line.getPrice());
                paid.setQuantity(a.coveredQty());
                paid.setPaymentId(payment.getId());
                OrderItemEntity savedPaid = orderItemRepository.save(paid);

                line.setQuantity(line.getQuantity() - a.coveredQty());
                orderItemRepository.save(line);

                covered.add(savedPaid.getId());
                splits.add(new Split(line.getId(), List.of(line.getId(), savedPaid.getId())));
            }
        }
        return new LinePaymentResult(payment.getId(), amount, covered, splits);
    }

    // --- helpers ---

    private PaymentEntity createPayment(
            LinePaymentRequest request, BigDecimal amount, BigDecimal tip, UserPrincipal me) {
        // protocol settles the lines but moves no money and takes no tip
        boolean protocol = request.method() == PaymentMethod.PROTOCOL;
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderPointId(request.orderPointId());
        payment.setAmount(protocol ? BigDecimal.ZERO : amount);
        payment.setTip(protocol ? BigDecimal.ZERO : tip);
        payment.setMethod(request.method());
        if (protocol) {
            payment.setFiscalStatus(FiscalStatus.PROTOCOL);
        }
        payment.setCreatedBy(me.username());
        payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    private BigDecimal unitPrice(OrderItemEntity line) {
        return line.getPrice() == null ? BigDecimal.ZERO : line.getPrice();
    }

    private BigDecimal lineTotal(OrderItemEntity line) {
        return unitPrice(line).multiply(BigDecimal.valueOf(line.getQuantity()));
    }

    private UserPrincipal requireAccessible(UUID orderPointId) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> notFound(orderPointId));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> notFound(orderPointId));
        if (!me.isSuper() && !Objects.equals(location.getClientId(), me.clientId())) {
            throw notFound(orderPointId);
        }
        return me;
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found: " + id);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
