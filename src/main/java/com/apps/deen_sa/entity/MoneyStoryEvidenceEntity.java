package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "money_story_evidence")
@Getter @Setter
public class MoneyStoryEvidenceEntity {
    @EmbeddedId private MoneyStoryEvidenceId id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("storyId") @JoinColumn(name = "story_id")
    private MoneyStoryEntity story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id")
    private FinancialTransactionEntity transaction;
    @Column(nullable = false) private BigDecimal amount;
    @Column(name = "occurred_at", nullable = false) private LocalDate occurredAt;
    @Column(name = "merchant_label") private String merchantLabel;
    @Column(name = "category_label") private String categoryLabel;

    @Embeddable @Getter @Setter
    public static class MoneyStoryEvidenceId implements java.io.Serializable {
        @Column(name = "story_id") private UUID storyId;
        @Column(name = "ordinal") private short ordinal;
    }
}
