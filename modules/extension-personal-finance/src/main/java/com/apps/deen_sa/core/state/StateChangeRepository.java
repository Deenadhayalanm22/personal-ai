package com.apps.deen_sa.finance.legacy.state;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Legacy state-store port retained while old finance rows are projected into the generic ledger.
 * Domain queries deliberately live in the owning finance extension.
 */
public interface StateChangeRepository extends JpaRepository<StateChangeEntity, Long>,
        JpaSpecificationExecutor<StateChangeEntity> { }
