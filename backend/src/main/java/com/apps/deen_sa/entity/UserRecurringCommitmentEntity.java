package com.apps.deen_sa.entity;

import com.apps.deen_sa.domain.CommitmentAmountMode;
import com.apps.deen_sa.domain.RecurringCommitmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** FIN-020 — user-owned planning rule; it is not a financial transaction. */
@Entity
@Table(name = "user_recurring_commitment")
@Getter @Setter
public class UserRecurringCommitmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUserEntity user;
    @Column(nullable = false, length = 120) private String label;
    @Enumerated(EnumType.STRING) @Column(name = "amount_mode", nullable = false, length = 30) private CommitmentAmountMode amountMode;
    @Column(name = "planning_amount", nullable = false, precision = 19, scale = 2) private BigDecimal planningAmount;
    @Column(name = "due_day") private Integer dueDay;
    @Column(name = "effective_month", nullable = false) private LocalDate effectiveMonth;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RecurringCommitmentStatus status = RecurringCommitmentStatus.ACTIVE;
    @Column(length = 100) private String category;
    @Column(length = 100) private String subcategory;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "merchant_id") private UserReferenceEntity merchant;
    @ManyToMany
    @JoinTable(name = "recurring_commitment_transaction", joinColumns = @JoinColumn(name = "commitment_id"), inverseJoinColumns = @JoinColumn(name = "transaction_id"))
    private List<FinancialTransactionEntity> linkedTransactions = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
}
