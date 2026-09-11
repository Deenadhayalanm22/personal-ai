package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import com.apps.deen_sa.domain.MoneyStoryLevel;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "money_story")
@Getter @Setter
public class MoneyStoryEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "snapshot_id")
    private MoneyStorySnapshotEntity snapshot;
    @Column(name = "story_key", nullable = false) private String storyKey;
    @Column(name = "story_type", nullable = false) private String storyType;
    @Enumerated(EnumType.STRING)
    @Column(name = "story_level", nullable = false) private MoneyStoryLevel storyLevel;
    @Column(name = "rule_version", nullable = false) private int ruleVersion;
    @Column(name = "template_version", nullable = false) private int templateVersion;
    @Column(name = "period_type", nullable = false) private String periodType;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "impact_amount", nullable = false) private BigDecimal impactAmount;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "content_hash", nullable = false) private String contentHash;
}
