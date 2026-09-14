package com.apps.deen_sa.entity;

import com.apps.deen_sa.domain.UserActionItemStatus;
import com.apps.deen_sa.domain.UserActionItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_action_item")
@Getter
@Setter
public class UserActionItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private UserActionItemType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserActionItemStatus status = UserActionItemStatus.OPEN;

    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "scheduled_completion_date", nullable = false)
    private LocalDate scheduledCompletionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
