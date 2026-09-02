package com.apps.deen_sa.v2.entity;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.domain.InputType;
import com.apps.deen_sa.v2.domain.MessageSource;
import com.apps.deen_sa.v2.domain.TransactionDraftStatus;
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

import java.time.Instant;

@Entity
@Table(name = "transaction_draft")
@Getter
@Setter
public class TransactionDraftEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Column(name = "input_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InputType inputType;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private MessageSource source;

    @Column(name = "source_message_id", nullable = false)
    private String sourceMessageId;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "transcribed_text", columnDefinition = "TEXT")
    private String transcribedText;

    @Column(name = "normalized_text", columnDefinition = "TEXT")
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionDraftStatus status = TransactionDraftStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
