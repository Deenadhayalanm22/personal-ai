package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_income_profile")
@Getter
@Setter
public class UserIncomeProfileEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "salary_visibility")
    private String salaryVisibility;
    @Column(name = "salary_range")
    private String salaryRange;
    @Column(name = "exact_monthly_salary")
    private BigDecimal exactMonthlySalary;
    @Column(name = "salary_frequency")
    private String salaryFrequency;
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
