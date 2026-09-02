package com.apps.deen_sa.v2.repository;

import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransactionEntity, Long> {
    Optional<FinancialTransactionEntity> findBySourceDraftId(Long sourceDraftId);

    @Query("""
            SELECT transaction
            FROM FinancialTransactionEntity transaction
            JOIN FETCH transaction.sourceDraft draft
            LEFT JOIN FETCH transaction.merchant merchant
            WHERE transaction.id = :id
              AND transaction.user.id = :userId
              AND transaction.deletedAt IS NULL
            """)
    Optional<FinancialTransactionEntity> findOwnedVisibleById(
            @Param("id") Long id,
            @Param("userId") Long userId);

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

    @Query("""
            SELECT transaction
            FROM FinancialTransactionEntity transaction
            JOIN FETCH transaction.sourceDraft draft
            LEFT JOIN FETCH transaction.merchant merchant
            WHERE transaction.user.id = :userId
              AND transaction.occurredAt >= :start
              AND transaction.occurredAt < :end
              AND transaction.deletedAt IS NULL
              AND (:category IS NULL OR transaction.category = :category)
              AND (:subcategory IS NULL OR transaction.subcategory = :subcategory)
              AND (:beforeId IS NULL OR transaction.id < :beforeId)
            ORDER BY transaction.id DESC
            """)
    List<FinancialTransactionEntity> findVisibleBefore(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("category") String category,
            @Param("subcategory") String subcategory,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(transaction), COALESCE(SUM(transaction.amount), 0)
            FROM FinancialTransactionEntity transaction
            WHERE transaction.user.id = :userId
              AND transaction.occurredAt >= :start
              AND transaction.occurredAt < :end
              AND transaction.deletedAt IS NULL
              AND (:category IS NULL OR transaction.category = :category)
              AND (:subcategory IS NULL OR transaction.subcategory = :subcategory)
            """)
    List<Object[]> summarizeVisible(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("category") String category,
            @Param("subcategory") String subcategory);

    @Query("""
            SELECT transaction.occurredAt, COUNT(transaction), SUM(transaction.amount)
            FROM FinancialTransactionEntity transaction
            WHERE transaction.user.id = :userId
              AND transaction.occurredAt >= :start
              AND transaction.occurredAt < :end
              AND transaction.deletedAt IS NULL
            GROUP BY transaction.occurredAt
            ORDER BY transaction.occurredAt
            """)
    List<Object[]> summarizeByDay(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
