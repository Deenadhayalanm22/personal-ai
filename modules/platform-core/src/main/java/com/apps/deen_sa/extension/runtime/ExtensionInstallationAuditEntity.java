package com.apps.deen_sa.extension.runtime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;

@Entity @Table(name="extension_installation_audit") @Getter @Setter
class ExtensionInstallationAuditEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id", nullable=false) private Long tenantId;
    @Column(name="extension_id", nullable=false, length=80) private String extensionId;
    @Column(name="extension_version", nullable=false, length=40) private String extensionVersion;
    @Column(nullable=false, length=30) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="configuration", columnDefinition="jsonb", nullable=false)
    private Map<String,Object> configuration;
    @Column(name="occurred_at", nullable=false) private Instant occurredAt;
}
