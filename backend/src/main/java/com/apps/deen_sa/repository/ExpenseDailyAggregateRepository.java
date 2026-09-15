package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.FinancialTransactionEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseDailyAggregateRepository
        extends Repository<FinancialTransactionEntity, Long> {

    @Modifying
    @Query(value = "INSERT INTO expense_aggregate_dirty_date (aggregate_date) VALUES (:date) ON CONFLICT DO NOTHING", nativeQuery = true)
    void markDateForRebuild(@Param("date") LocalDate date);

    @Query(value = "SELECT aggregate_date FROM expense_aggregate_dirty_date ORDER BY aggregate_date LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<java.sql.Date> lockDatesNeedingRebuild(@Param("limit") int limit);

    @Modifying
    @Query(value = "DELETE FROM expense_aggregate_dirty_date WHERE aggregate_date = :date", nativeQuery = true)
    void clearRebuildRequest(@Param("date") LocalDate date);

    @Query(value = """
            SELECT DISTINCT ft.occurred_at
            FROM financial_transaction ft
            WHERE ft.occurred_at < :exclusiveEnd
              AND ft.deleted_at IS NULL
              AND ft.category IS NOT NULL
              AND ft.subcategory IS NOT NULL
              AND ft.spending_nature IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM expense_daily_aggregate eda
                  WHERE eda.aggregate_date = ft.occurred_at
                    AND eda.user_id = ft.user_id
                    AND eda.category = ft.category
                    AND eda.subcategory = ft.subcategory
                    AND eda.spending_nature = ft.spending_nature
              )
            ORDER BY ft.occurred_at
            """, nativeQuery = true)
    List<java.sql.Date> findMissingDatesBefore(@Param("exclusiveEnd") LocalDate exclusiveEnd);

    @Modifying
    @Query(value = "DELETE FROM expense_daily_aggregate WHERE aggregate_date = :date",
            nativeQuery = true)
    int deleteForDate(@Param("date") LocalDate date);

    @Modifying
    @Query(value = """
            INSERT INTO expense_daily_aggregate (
                user_id, aggregate_date, category, subcategory, spending_nature,
                total_amount, transaction_count, min_amount, max_amount,
                created_at, updated_at
            )
            SELECT
                ft.user_id,
                ft.occurred_at,
                ft.category,
                ft.subcategory,
                ft.spending_nature,
                CAST(SUM(ft.amount) AS NUMERIC(15,2)),
                CAST(COUNT(*) AS INTEGER),
                CAST(MIN(ft.amount) AS NUMERIC(15,2)),
                CAST(MAX(ft.amount) AS NUMERIC(15,2)),
                NOW(),
                NOW()
            FROM financial_transaction ft
            WHERE ft.occurred_at = :date
              AND ft.deleted_at IS NULL
              AND ft.category IS NOT NULL
              AND ft.subcategory IS NOT NULL
              AND ft.spending_nature IS NOT NULL
            GROUP BY
                ft.user_id, ft.occurred_at, ft.category, ft.subcategory, ft.spending_nature
            """, nativeQuery = true)
    int aggregateForDate(@Param("date") LocalDate date);

    @Modifying
    @Query(value = "DELETE FROM expense_daily_reference_aggregate WHERE aggregate_date = :date",
            nativeQuery = true)
    int deleteReferenceForDate(@Param("date") LocalDate date);

    @Modifying
    @Query(value = """
            INSERT INTO expense_daily_reference_aggregate (
                user_id, aggregate_date, reference_entity_id, reference_type,
                total_amount, transaction_count, min_amount, max_amount, created_at, updated_at
            )
            SELECT ft.user_id, ft.occurred_at, ft.merchant_id, 'MERCHANT',
                   CAST(SUM(ft.amount) AS NUMERIC(19,2)), CAST(COUNT(*) AS INTEGER),
                   CAST(MIN(ft.amount) AS NUMERIC(19,2)), CAST(MAX(ft.amount) AS NUMERIC(19,2)),
                   NOW(), NOW()
            FROM financial_transaction ft
            WHERE ft.occurred_at = :date
              AND ft.deleted_at IS NULL
              AND ft.merchant_id IS NOT NULL
              AND ft.category IS NOT NULL
              AND ft.subcategory IS NOT NULL
              AND ft.spending_nature IS NOT NULL
            GROUP BY ft.user_id, ft.occurred_at, ft.merchant_id
            """, nativeQuery = true)
    int aggregateReferencesForDate(@Param("date") LocalDate date);
}
