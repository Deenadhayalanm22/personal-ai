package com.apps.deen_sa.finance.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fin_expense_projection", uniqueConstraints = @UniqueConstraint(name = "uq_fin_expense_event", columnNames = "core_event_id"))
@Getter @Setter
public class FinanceExpenseProjectionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "core_event_id", nullable = false) private Long coreEventId;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "user_id", nullable = false) private String userId;
    @Column(nullable = false) private BigDecimal amount;
    private String category;
    private String subcategory;
    @Column(name = "source_account") private String sourceAccount;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "legacy_transaction_id") private Long legacyTransactionId;
    @Column(nullable = false) private boolean active = true;
}
