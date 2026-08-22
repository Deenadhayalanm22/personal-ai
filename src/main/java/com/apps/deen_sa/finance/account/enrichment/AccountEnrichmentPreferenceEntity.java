package com.apps.deen_sa.finance.account.enrichment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "account_enrichment_preference",
        uniqueConstraints = @UniqueConstraint(name = "uq_account_enrichment_field", columnNames = {"account_id", "field_name"}))
public class AccountEnrichmentPreferenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long accountId;
    @Column(nullable = false, length = 100)
    private String fieldName;
    @Column(nullable = false, length = 30)
    private String promptStatus = "PENDING";
    private Instant remindAfter;
    private Instant lastPromptedAt;
    @Column(nullable = false)
    private Integer promptCount = 0;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
