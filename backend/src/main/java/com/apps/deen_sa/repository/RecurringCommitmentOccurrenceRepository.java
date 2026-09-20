package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.RecurringCommitmentOccurrenceEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringCommitmentOccurrenceRepository extends JpaRepository<RecurringCommitmentOccurrenceEntity, Long> {
    Optional<RecurringCommitmentOccurrenceEntity> findByCommitmentIdAndScheduledMonth(Long commitmentId, LocalDate scheduledMonth);
    void deleteByCommitmentId(Long commitmentId);
}
