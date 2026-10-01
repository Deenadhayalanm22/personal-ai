package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.RecurringCommitmentExtraEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringCommitmentExtraRepository extends JpaRepository<RecurringCommitmentExtraEntity, Long> {
    java.util.Optional<RecurringCommitmentExtraEntity> findByPaymentTransactionId(Long transactionId);
    java.util.Optional<RecurringCommitmentExtraEntity> findByOccurrenceIdAndRequestId(Long occurrenceId, String requestId);
    List<RecurringCommitmentExtraEntity> findByOccurrenceIdOrderByCreatedAtAsc(Long occurrenceId);
}
