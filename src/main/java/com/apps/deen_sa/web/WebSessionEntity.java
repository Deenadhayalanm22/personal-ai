package com.apps.deen_sa.web;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "web_session", uniqueConstraints =
        @UniqueConstraint(name = "uq_web_session_token_hash", columnNames = "token_hash"))
@Getter
@Setter
public class WebSessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
