package com.apps.deen_sa.entity;

import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.domain.LoanType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_loan")
@Getter
@Setter
public class UserLoanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Column(name = "loan_name", nullable = false)
    private String loanName;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 30)
    private LoanType loanType;

    @Column(name = "lender_name", nullable = false)
    private String lenderName;

    @Column(name = "original_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalPrincipal;

    @Column(name = "monthly_emi_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyEmiAmount;

    @Column(name = "total_tenure_months", nullable = false)
    private Integer totalTenureMonths;

    @Column(name = "first_emi_due_date", nullable = false)
    private LocalDate firstEmiDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
