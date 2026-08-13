package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints =
        @UniqueConstraint(name = "uq_app_user_channel_external", columnNames = {"channel", "external_user_id"}))
@Getter
@Setter
public class AppUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(name = "external_user_id", nullable = false)
    private String externalUserId;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Column(nullable = false, length = 20)
    private String locale = "en-IN";

    @Column(nullable = false, length = 60)
    private String timezone = "Asia/Kolkata";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
