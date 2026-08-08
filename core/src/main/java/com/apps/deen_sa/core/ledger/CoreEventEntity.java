package com.apps.deen_sa.core.ledger;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity @Table(name = "core_event", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"}))
@Getter @Setter
public class CoreEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id", nullable=false) private Long tenantId;
    @Column(name="extension_id", nullable=false, length=80) private String extensionId;
    @Column(name="event_type", nullable=false, length=100) private String eventType;
    @Column(name="schema_version", nullable=false, length=40) private String schemaVersion;
    @Column(name="occurred_at", nullable=false) private Instant occurredAt;
    @Column(name="recorded_at", nullable=false) private Instant recordedAt;
    @Column(name="actor_id", nullable=false, length=100) private String actorId;
    @Column(nullable=false, length=30) private String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="facts", columnDefinition="jsonb", nullable=false) private Map<String,Object> facts;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="evidence", columnDefinition="jsonb", nullable=false) private Map<String,Object> evidence;
    @Column(name="rule_version", nullable=false, length=40) private String ruleVersion;
    @Column(name="idempotency_key", nullable=false, length=160) private String idempotencyKey;
    @Column(name="causation_id", length=160) private String causationId;
}
