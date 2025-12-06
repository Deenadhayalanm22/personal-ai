package com.apps.deen_sa.repo;

import com.apps.deen_sa.entity.ExpenseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    @Query("""
    SELECT e.category AS category, SUM(e.amount) AS total
    FROM ExpenseEntity e
    WHERE e.spentAt >= :startOfMonth 
      AND e.spentAt < :startOfNextMonth
    GROUP BY e.category""")
    List<Object[]> getMonthlyTotalsByCategory(
            @Param("startOfMonth") OffsetDateTime startOfMonth,
            @Param("startOfNextMonth") OffsetDateTime startOfNextMonth
    );

    @Query("""
    SELECT e FROM ExpenseEntity e
    ORDER BY e.spentAt DESC, e.id DESC
    """)
    List<ExpenseEntity> findTop5Expenses(Pageable pageable);
}

