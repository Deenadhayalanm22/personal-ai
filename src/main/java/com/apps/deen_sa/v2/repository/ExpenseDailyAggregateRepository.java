package com.apps.deen_sa.v2.repository;

import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseDailyAggregateRepository
        extends Repository<FinancialTransactionEntity, Long> {

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
                    AND eda.user_id = CAST(ft.user_id AS VARCHAR(50))
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
                CAST(ft.user_id AS VARCHAR(50)),
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
}
