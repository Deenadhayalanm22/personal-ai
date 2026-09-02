package com.apps.deen_sa.v2.repository;

import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransactionEntity, Long> {
    Optional<FinancialTransactionEntity> findBySourceDraftId(Long sourceDraftId);

    @Query("""
            SELECT COALESCE(transaction.category, 'Uncategorized'), SUM(transaction.amount)
            FROM FinancialTransactionEntity transaction
            WHERE transaction.user.id = :userId
              AND transaction.occurredAt >= :start
              AND transaction.occurredAt < :end
              AND transaction.deletedAt IS NULL
            GROUP BY COALESCE(transaction.category, 'Uncategorized')
            ORDER BY COALESCE(transaction.category, 'Uncategorized')
            """)
    List<Object[]> sumByCategoryForPeriod(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    long countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanAndDeletedAtIsNull(
            Long userId, LocalDate start, LocalDate end);
}
