package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "expense")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -----------------------------
    // User & Account References
    // -----------------------------
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id")
    private Long accountId;

    // -----------------------------
    // Core Finance Fields
    // -----------------------------
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @Column(length = 50, nullable = false)
    private String category;             // Predefined set (Transport, Food, Health)

    @Column(length = 100)
    private String subcategory;          // Flexible based on user-generated

    @Column(name = "merchant_name", length = 200)
    private String merchantName;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    // -----------------------------
    // Date & Time
    // -----------------------------
    @Column(name = "spent_at", nullable = false)
    private OffsetDateTime spentAt;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    // -----------------------------
    // Raw Input & Flexible Metadata
    // -----------------------------
    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;   // Vehicle, location, custom tags, notes

    // -----------------------------
    // Tags
    // -----------------------------
    @ElementCollection
    @CollectionTable(name = "expense_tags",
            joinColumns = @JoinColumn(name = "expense_id"))
    @Column(name = "tag")
    private List<String> tags;

    // -----------------------------
    // Validation
    // -----------------------------
    @Column(name = "is_valid")
    private Boolean isValid = true;

    @Column(name = "validation_reason", columnDefinition = "TEXT")
    private String validationReason;

    // -----------------------------
    // Source
    // -----------------------------
    @Column(length = 50)
    private String source;                // voice, manual, import, llm

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    // -----------------------------
    // Audit Fields
    // -----------------------------
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

