package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.MoneyStorySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MoneyStorySnapshotRepository extends JpaRepository<MoneyStorySnapshotEntity, UUID> {
    Optional<MoneyStorySnapshotEntity> findByUserIdAndScopeMonthAndSupersededAtIsNull(Long userId, LocalDate month);
}
