package com.apps.deen_sa.core.ledger;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Table(name="core_movement") @Getter @Setter
public class CoreMovementEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_id", nullable=false) private Long eventId;
    @Column(name="resource_id", nullable=false, length=120) private String resourceId;
    @Column(name="container_id", nullable=false, length=120) private String containerId;
    @Column(nullable=false, precision=19, scale=6) private BigDecimal quantity;
    @Column(name="unit_id", nullable=false, length=40) private String unitId;
}
