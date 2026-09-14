package com.apps.deen_sa.entity;

import com.apps.deen_sa.domain.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "investment_transaction")
@Getter
@Setter
public class InvestmentTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_investment_id", nullable = false)
    private UserInvestmentEntity investment;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_kind", nullable = false, length = 30)
    private InvestmentTransactionKind transactionKind;
    @Column(name = "scheduled_month")
    private LocalDate scheduledMonth;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvestmentTransactionStatus status;
    @Column(name = "transaction_date")
    private LocalDate transactionDate;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "unit_price", precision = 19, scale = 6)
    private BigDecimal unitPrice;
    @Column(precision = 19, scale = 6)
    private BigDecimal units;
    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_source", length = 30)
    private InvestmentCalculationSource calculationSource;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
