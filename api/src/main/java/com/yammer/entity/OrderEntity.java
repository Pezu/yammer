package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_no", nullable = false)
    private Long orderNo;

    @Column(name = "order_point_id", nullable = false)
    private UUID orderPointId;

    /** The event this order belongs to (snapshotted from the order point at placement). */
    @Column(name = "event_id")
    private UUID eventId;

    /** The customer who placed it (self-service pay-now orders); null otherwise. */
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String status;
}
