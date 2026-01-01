package com.apps.deen_sa.core.state;

import com.apps.deen_sa.dto.TimeRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface StateChangeRepository extends JpaRepository<StateChangeEntity, Long>, JpaSpecificationExecutor<StateChangeEntity> {

    @Query(value = """
            SELECT COALESCE(SUM(t.amount), 0)
            FROM transaction_rec t
            LEFT JOIN value_container vc
                   ON vc.id = t.source_container_id
            WHERE t.transaction_type = 'EXPENSE'
              AND t.tx_time BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
              AND (:sourceAccount IS NULL OR vc.container_type = :sourceAccount)
            """, nativeQuery = true)
    BigDecimal sumExpenses(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("category") String category,
            @Param("sourceAccount") String sourceAccount

    );

    @Query(value = """
                SELECT t.category, COALESCE(SUM(t.amount), 0)
                FROM transaction_rec t
                LEFT JOIN value_container vc
                       ON vc.id = t.source_container_id
                WHERE t.transaction_type = 'EXPENSE'
                  AND t.tx_time BETWEEN :start AND :end
                  AND (:sourceAccount IS NULL OR vc.container_type = :sourceAccount)
                GROUP BY t.category
                ORDER BY SUM(t.amount) DESC
                """, nativeQuery = true)
    List<Object[]> sumByCategoryRaw(
            Instant start,
            Instant end,
            String sourceAccount
    );

    @Query(value = """
            SELECT
                vc.container_type AS source_account,
                COALESCE(SUM(t.amount), 0) AS total_amount
            FROM transaction_rec t
            LEFT JOIN value_container vc
                   ON vc.id = t.source_container_id
            WHERE t.transaction_type = 'EXPENSE'
              AND t.tx_time BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
            GROUP BY vc.container_type
            ORDER BY total_amount DESC
            """, nativeQuery = true)
    List<Object[]> sumBySourceAccountRaw(
            Instant start,
            Instant end,
            String category
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM transaction_rec
                    WHERE category = 'Repayment'
                      AND subcategory = 'Loan EMI'
                      AND details ->> 'loanContainerId' = CAST(:loanId AS TEXT)
                    """,
            nativeQuery = true
    )
    int countLoanEmis(@Param("loanId") Long loanId);

    // ✅ THIS METHOD GOES HERE
    default Map<String, BigDecimal> sumByCategory(
            TimeRange range,
            String sourceAccount
    ) {
        return sumByCategoryRaw(
                range.start(),
                range.end(),
                sourceAccount
        ).stream().collect(
                Collectors.toMap(
                        r -> (String) r[0],
                        r -> (BigDecimal) r[1],
                        (a, b) -> a,              // merge function (won't happen due to GROUP BY)
                        LinkedHashMap::new
                )
        );
    }

    // ✅ THIS METHOD GOES HERE
    default Map<String, BigDecimal> sumBySourceAccount(
            TimeRange range,
            String sourceAccount
    ) {
        return sumBySourceAccountRaw(
                range.start(),
                range.end(),
                sourceAccount
        ).stream().collect(
                Collectors.toMap(
                        r -> (String) r[0],
                        r -> (BigDecimal) r[1],
                        (a, b) -> a,              // merge function (won't happen due to GROUP BY)
                        LinkedHashMap::new
                )
        );
    }
}
