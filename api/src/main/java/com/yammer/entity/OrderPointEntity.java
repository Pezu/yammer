package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_point")
@Getter
@Setter
public class OrderPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Column(name = "pay_later", nullable = false)
    private boolean payLater;

    @Column(nullable = false)
    private boolean protocol;

    @Column(name = "menu_id")
    private UUID menuId;

    /** For a pay-later point: the non-pay-later point that serves it. */
    @Column(name = "service_order_point_id")
    private UUID serviceOrderPointId;

    /** Printer integration assigned to this order point. */
    @Column(name = "printer_id")
    private UUID printerId;

    /** Cash register integration assigned to this order point. */
    @Column(name = "cash_register_id")
    private UUID cashRegisterId;
}
