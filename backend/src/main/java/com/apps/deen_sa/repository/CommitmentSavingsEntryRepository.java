package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.CommitmentSavingsEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommitmentSavingsEntryRepository extends JpaRepository<CommitmentSavingsEntryEntity, Long> {
    Optional<CommitmentSavingsEntryEntity> findByPlanIdAndScheduledMonth(Long planId, LocalDate scheduledMonth);
    List<CommitmentSavingsEntryEntity> findByPlanIdOrderByScheduledMonthAsc(Long planId);
}
