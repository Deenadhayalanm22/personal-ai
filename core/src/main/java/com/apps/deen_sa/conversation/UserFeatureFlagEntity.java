package com.apps.deen_sa.conversation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_feature_flag", uniqueConstraints =
        @UniqueConstraint(name = "uq_user_feature_flag_subject", columnNames = {"channel", "external_user_id", "feature_key"}))
@Getter
@Setter
public class UserFeatureFlagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(name = "external_user_id", nullable = false, length = 100)
    private String externalUserId;

    @Column(name = "feature_key", nullable = false, length = 80)
    private String featureKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void markUpdated() {
        updatedAt = Instant.now();
    }
}
