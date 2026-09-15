package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Canonical, versioned monthly read model used by stories and later AI-safe monthly context. */
@Entity
@Table(name = "monthly_financial_snapshot")
@Getter @Setter
public class MonthlyFinancialSnapshotEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;
    @Column(name = "scope_month", nullable = false) private LocalDate scopeMonth;
    @Column(name = "calculation_version", nullable = false) private int calculationVersion;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Column(name = "source_fingerprint", nullable = false, length = 64) private String sourceFingerprint;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
