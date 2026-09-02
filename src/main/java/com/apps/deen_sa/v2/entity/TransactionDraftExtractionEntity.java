package com.apps.deen_sa.v2.entity;

import com.apps.deen_sa.v2.domain.TransactionDraftExtractionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transaction_draft_extraction")
@Getter
@Setter
public class TransactionDraftExtractionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private TransactionDraftEntity draft;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "category_id", length = 100)
    private String categoryId;

    @Column(name = "subcategory_id", length = 100)
    private String subcategoryId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDate occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionDraftExtractionStatus status;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
