package com.apps.deen_sa.entity;

import com.apps.deen_sa.domain.RecurringCommitmentOccurrenceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_commitment_occurrence", uniqueConstraints = @UniqueConstraint(columnNames = {"commitment_id", "scheduled_month"}))
@Getter @Setter
public class RecurringCommitmentOccurrenceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "commitment_id", nullable = false) private UserRecurringCommitmentEntity commitment;
    @Column(name = "scheduled_month", nullable = false) private LocalDate scheduledMonth;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RecurringCommitmentOccurrenceStatus status;
    private LocalDate completedAt;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();
}
