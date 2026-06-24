package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A parked self-service cart awaiting online (Netopia) payment. While PENDING no order exists; on a
 * confirmed gateway IPN the real order + {@link PaymentEntity} are created and linked here. The
 * row's {@code id} is the reference passed to Netopia as the order id and echoed back in the IPN.
 */
@Entity
@Table(name = "online_payment")
@Getter
@Setter
public class OnlinePaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_point_id", nullable = false)
    private UUID orderPointId;

    @Column(name = "event_id")
    private UUID eventId;

    /** The customer placing the order (resolved at start), carried to the order on confirm. */
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** JSON snapshot of the validated cart: [{menuItemId,name,price,quantity}]. */
    @Column(nullable = false)
    private String items;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnlinePaymentStatus status = OnlinePaymentStatus.PENDING;

    /** Netopia transaction id (ntpID). */
    @Column(name = "ntp_id")
    private String ntpId;

    /** The order created once payment is confirmed. */
    @Column(name = "order_id")
    private UUID orderId;

    /** The payment created once payment is confirmed. */
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
