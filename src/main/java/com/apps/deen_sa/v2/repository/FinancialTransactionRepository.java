package com.apps.deen_sa.v2.repository;

import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransactionEntity, Long> {
    Optional<FinancialTransactionEntity> findBySourceDraftId(Long sourceDraftId);
}
