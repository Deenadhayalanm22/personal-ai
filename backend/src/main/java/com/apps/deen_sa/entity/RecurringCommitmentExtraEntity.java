package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recurring_commitment_extra")
@Getter @Setter
public class RecurringCommitmentExtraEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "occurrence_id", nullable = false) private RecurringCommitmentOccurrenceEntity occurrence;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 200) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
