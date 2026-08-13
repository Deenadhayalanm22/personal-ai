package com.apps.deen_sa.finance.budget;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fin_monthly_budget", uniqueConstraints =
        @UniqueConstraint(name = "uq_fin_monthly_budget_user_category", columnNames = {"user_id", "category"}))
@Getter @Setter
public class MonthlyBudgetEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 100) private String category;
    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 4) private BigDecimal monthlyLimit;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
