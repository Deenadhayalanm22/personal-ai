package com.apps.deen_sa.saree;

import com.apps.deen_sa.core.ledger.CoreMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

interface SareeLedgerQueryRepository extends JpaRepository<CoreMovementEntity, Long> {
    @Query(value = """
            SELECT COALESCE(SUM(m.quantity), 0) FROM core_movement m
            JOIN core_event e ON e.id = m.event_id
            WHERE e.tenant_id = :tenantId AND e.extension_id = 'saree-job-work'
              AND m.resource_id = :resource AND m.container_id = :container AND m.unit_id = :unit
            """, nativeQuery = true)
    BigDecimal balance(@Param("tenantId") Long tenantId, @Param("resource") String resource,
                       @Param("container") String container, @Param("unit") String unit);
}
