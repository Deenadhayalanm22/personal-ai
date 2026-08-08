package com.apps.deen_sa.finance.persistence;

import com.apps.deen_sa.dto.TimeRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Finance-owned read model over the legacy projection. */
public interface FinanceAnalyticsRepository extends JpaRepository<FinanceExpenseProjectionEntity, Long> {
    boolean existsByCoreEventId(Long coreEventId);
    @Query(value = """
            SELECT COALESCE(SUM(t.amount), 0) FROM fin_expense_projection t
            WHERE t.user_id = :userId
              AND t.occurred_at BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
              AND (:sourceAccount IS NULL OR t.source_account = :sourceAccount)
            """, nativeQuery = true)
    BigDecimal sumExpenses(@Param("userId") String userId, @Param("start") Instant start,
                           @Param("end") Instant end, @Param("category") String category,
                           @Param("sourceAccount") String sourceAccount);

    @Query(value = """
            SELECT t.category, COALESCE(SUM(t.amount), 0) FROM fin_expense_projection t
            WHERE t.user_id = :userId AND t.occurred_at BETWEEN :start AND :end
              AND (:sourceAccount IS NULL OR t.source_account = :sourceAccount)
            GROUP BY t.category ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumByCategoryRaw(@Param("userId") String userId, Instant start, Instant end, String sourceAccount);

    @Query(value = """
            SELECT t.subcategory, COALESCE(SUM(t.amount), 0) FROM fin_expense_projection t
            WHERE t.user_id = :userId AND t.occurred_at BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
              AND (:sourceAccount IS NULL OR t.source_account = :sourceAccount)
            GROUP BY t.subcategory ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumBySubcategoryRaw(@Param("userId") String userId, Instant start, Instant end,
                                       String category, String sourceAccount);

    @Query(value = """
            SELECT t.source_account, COALESCE(SUM(t.amount), 0) FROM fin_expense_projection t
            WHERE t.user_id = :userId AND t.occurred_at BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
            GROUP BY t.source_account ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumBySourceAccountRaw(@Param("userId") String userId, Instant start, Instant end, String category);

    @Query(value = """
            SELECT COUNT(*) FROM state_change
            WHERE category = 'Repayment' AND subcategory = 'Loan EMI'
              AND details ->> 'loanContainerId' = CAST(:loanId AS TEXT)
            """, nativeQuery = true)
    int countLoanEmis(@Param("loanId") Long loanId);

    default Map<String, BigDecimal> sumByCategory(String userId, TimeRange range, String sourceAccount) {
        return map(sumByCategoryRaw(userId, range.start(), range.end(), sourceAccount));
    }
    default Map<String, BigDecimal> sumBySourceAccount(String userId, TimeRange range, String category) {
        return map(sumBySourceAccountRaw(userId, range.start(), range.end(), category));
    }
    default Map<String, BigDecimal> sumBySubcategory(String userId, TimeRange range, String category, String sourceAccount) {
        return map(sumBySubcategoryRaw(userId, range.start(), range.end(), category, sourceAccount));
    }
    private Map<String, BigDecimal> map(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (String) row[0], row -> (BigDecimal) row[1],
                (left, right) -> left, LinkedHashMap::new));
    }
}
