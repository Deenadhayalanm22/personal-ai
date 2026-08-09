package com.apps.deen_sa.saree;

import com.apps.deen_sa.core.ledger.CoreEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SareeWorkflowRepository extends JpaRepository<CoreEventEntity, Long> {
    List<CoreEventEntity> findByTenantIdAndExtensionIdAndEventTypeOrderByIdDesc(
            Long tenantId, String extensionId, String eventType);

    long countByTenantIdAndExtensionIdAndEventType(Long tenantId, String extensionId, String eventType);
}
