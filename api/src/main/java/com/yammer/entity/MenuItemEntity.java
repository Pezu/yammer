package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "menu_item")
@Getter
@Setter
public class MenuItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean orderable;

    private BigDecimal price;

    @Column(name = "vat_type_id")
    private UUID vatTypeId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
