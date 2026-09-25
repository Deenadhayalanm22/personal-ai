package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "commitment_savings_entry") @Getter @Setter
public class CommitmentSavingsEntryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "plan_id") private CommitmentSavingsPlanEntity plan;
    @Column(name = "scheduled_month", nullable = false) private LocalDate scheduledMonth;
    @Column(nullable = false) private String status;
    private BigDecimal amount;
    @Column(name = "recorded_at") private LocalDate recordedAt;
}
