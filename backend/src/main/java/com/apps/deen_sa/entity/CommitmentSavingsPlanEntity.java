package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "commitment_savings_plan") @Getter @Setter
public class CommitmentSavingsPlanEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "commitment_id") private UserRecurringCommitmentEntity commitment;
    @Column(name = "target_date", nullable = false) private LocalDate targetDate;
    @Column(name = "target_amount", nullable = false) private BigDecimal targetAmount;
    @Column(name = "start_month", nullable = false) private LocalDate startMonth;
    @Column(name = "monthly_amount", nullable = false) private BigDecimal monthlyAmount;
    @Column(name = "final_amount", nullable = false) private BigDecimal finalAmount;
    @Column(name = "used_amount", nullable = false) private BigDecimal usedAmount = BigDecimal.ZERO;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
