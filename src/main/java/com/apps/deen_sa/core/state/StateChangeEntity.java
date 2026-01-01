package com.apps.deen_sa.core.state;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "state_change")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class StateChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenant ready without drama
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "business_id")
    private String businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private StateChangeTypeEnum transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unit; // pcs, kg, litre, N/A

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String subcategory;

    @Column(name = "main_entity", length = 150)
    private String mainEntity;
    // merchant / vendor / employee / client

    @Column(name = "tx_time", nullable = false)
    private Instant timestamp;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    // Flexible payload for everything else
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "source_container_id")
    private Long sourceContainerId;

    @Column(name = "target_container_id")
    private Long targetContainerId;

    // -----------------------------
    // Tags
    // -----------------------------
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    // Optional but useful
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    // existing fields...

    @Enumerated(EnumType.STRING)
    @Column(name = "completeness_level", nullable = false)
    private CompletenessLevelEnum completenessLevel;

    @Column(name = "financially_applied", nullable = false)
    private boolean financiallyApplied = false;

    @Column(name = "needs_enrichment", nullable = false)
    private boolean needsEnrichment = false;


}
