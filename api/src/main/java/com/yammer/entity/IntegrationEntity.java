package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "integration")
@Getter
@Setter
public class IntegrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private String name;

    @Column
    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationType type;
}
