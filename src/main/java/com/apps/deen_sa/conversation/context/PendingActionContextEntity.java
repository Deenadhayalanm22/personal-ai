package com.apps.deen_sa.conversation.context;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "pending_action_context")
@Getter
@Setter
public class PendingActionContextEntity {
    @Id
    @Column(length = 40)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "context_type", nullable = false, length = 50)
    private String contextType;
    @Column(name = "context_value", nullable = false, length = 500)
    private String contextValue;
    @Column(length = 60)
    private String timezone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PendingActionContextStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "replaced_at")
    private Instant replacedAt;
}
