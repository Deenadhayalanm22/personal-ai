package com.apps.deen_sa.finance.expense.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "expense_draft", uniqueConstraints =
        @UniqueConstraint(name = "uq_expense_draft_source_message", columnNames = {"source_channel", "source_message_id"}))
@Getter
@Setter
public class ExpenseDraftEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "source_channel", nullable = false, length = 30)
    private String sourceChannel;
    @Column(name = "source_message_id")
    private String sourceMessageId;
    @Column(name = "source_session_id")
    private Long sourceSessionId;
    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "partial_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> partialJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_fields", nullable = false, columnDefinition = "jsonb")
    private List<String> missingFields;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseDraftStatus status = ExpenseDraftStatus.PENDING;
    @Column(nullable = false)
    private int version = 1;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "discarded_at")
    private Instant discardedAt;
    @Column(name = "completed_transaction_id")
    private Long completedTransactionId;
}
