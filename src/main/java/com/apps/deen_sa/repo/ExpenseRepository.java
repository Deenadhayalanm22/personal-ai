package com.apps.deen_sa.repo;

import com.apps.deen_sa.entity.ExpenseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    @Query("SELECT SUM(e.amount) FROM ExpenseEntity e WHERE DATE(e.spentAt) = :date")
    Optional<BigDecimal> sumAmountByDate(@Param("date") LocalDate date);

    @Query("""
        SELECT SUM(e.amount)
        FROM ExpenseEntity e
        WHERE DATE(e.spentAt) BETWEEN :start AND :end
    """)
    Optional<BigDecimal> sumAmountBetweenDates(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query("""
        SELECT e.category, SUM(e.amount)
        FROM ExpenseEntity e
        WHERE DATE(e.spentAt) BETWEEN :start AND :end
        GROUP BY e.category
    """)
    List<Object[]> rawCategoryTotals(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    default Map<String, BigDecimal> sumAmountGroupByCategory(LocalDate start, LocalDate end) {
        List<Object[]> rows = rawCategoryTotals(start, end);
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((String) r[0], (BigDecimal) r[1]);
        }
        return map;
    }

    List<ExpenseEntity> findTop10ByOrderBySpentAtDesc();
}

