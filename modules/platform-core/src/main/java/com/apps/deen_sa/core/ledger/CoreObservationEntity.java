package com.apps.deen_sa.core.ledger;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="core_observation") @Getter @Setter
public class CoreObservationEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_id", nullable=false) private Long eventId;
    @Column(name="subject_type", nullable=false, length=80) private String subjectType;
    @Column(name="subject_id", nullable=false, length=120) private String subjectId;
    @Column(nullable=false, precision=19, scale=6) private BigDecimal value;
    @Column(name="unit_id", nullable=false, length=40) private String unitId;
    @Column(name="observed_at", nullable=false) private Instant observedAt;
}
