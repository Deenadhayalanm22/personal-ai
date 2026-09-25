package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.CommitmentSavingsPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommitmentSavingsPlanRepository extends JpaRepository<CommitmentSavingsPlanEntity, Long> {
    Optional<CommitmentSavingsPlanEntity> findByCommitmentIdAndTargetDate(Long commitmentId, LocalDate targetDate);
    List<CommitmentSavingsPlanEntity> findByCommitmentUserId(Long userId);
    List<CommitmentSavingsPlanEntity> findByCommitmentIdOrderByTargetDateDesc(Long commitmentId);
}
