package com.apps.deen_sa.finance.expense.draft;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseDraftRepository extends JpaRepository<ExpenseDraftEntity, Long> {
    List<ExpenseDraftEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(
            Long userId, ExpenseDraftStatus status, Pageable pageable);

    Optional<ExpenseDraftEntity> findBySourceChannelAndSourceMessageId(String sourceChannel, String sourceMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ExpenseDraftEntity d WHERE d.id = :id AND d.userId = :userId")
    Optional<ExpenseDraftEntity> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
