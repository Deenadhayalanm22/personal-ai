package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "money_story_snapshot")
@Getter @Setter
public class MoneyStorySnapshotEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private AppUserEntity user;
    @Column(name = "scope_month", nullable = false) private LocalDate scopeMonth;
    @Column(nullable = false) private String timezone;
    @Column(nullable = false) private String locale;
    @Column(nullable = false) private String currency;
    @Column(nullable = false) private String status;
    @Column(name = "input_watermark", nullable = false) private Instant inputWatermark;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "evaluated_on") private LocalDate evaluatedOn;
    @Column(name = "content_fingerprint") private String contentFingerprint;
    @Column(name = "calculation_version", nullable = false) private int calculationVersion;
}
