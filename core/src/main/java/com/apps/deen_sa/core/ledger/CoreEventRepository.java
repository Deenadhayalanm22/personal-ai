package com.apps.deen_sa.core.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CoreEventRepository extends JpaRepository<CoreEventEntity, Long> {
    Optional<CoreEventEntity> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);
}
