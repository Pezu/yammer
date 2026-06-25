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

/** One component row of a combined product's recipe (Rețetar): name, quantity, unit, percentage. */
@Entity
@Table(name = "recipe_component")
@Getter
@Setter
public class RecipeComponentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    /** The referenced (non-combined) product this recipe row is made of. */
    @Column(name = "component_item_id")
    private UUID componentItemId;

    /** Display snapshot of the referenced product's name (source of truth is componentItemId). */
    private String name;

    private BigDecimal quantity;

    private String unit;

    private BigDecimal percentage;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
