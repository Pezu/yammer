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

/** A browser/device Web Push subscription for a staff user (waiter PWA notifications). */
@Entity
@Table(name = "push_subscription")
@Getter
@Setter
public class PushSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String username;

    /** Push service endpoint URL (unique per subscription). */
    @Column(nullable = false, unique = true)
    private String endpoint;

    /** Client public key (base64url) for payload encryption. */
    @Column(nullable = false)
    private String p256dh;

    /** Client auth secret (base64url). */
    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
