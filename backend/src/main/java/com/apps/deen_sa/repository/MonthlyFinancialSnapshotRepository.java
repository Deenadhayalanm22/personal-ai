package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.MonthlyFinancialSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyFinancialSnapshotRepository extends JpaRepository<MonthlyFinancialSnapshotEntity, UUID> {
    Optional<MonthlyFinancialSnapshotEntity> findByUserIdAndScopeMonth(Long userId, LocalDate scopeMonth);
}
