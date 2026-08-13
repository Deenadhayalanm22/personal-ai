package com.apps.deen_sa.finance.legacy.state;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

/**
 * Legacy state-store port retained while old finance rows are projected into the generic ledger.
 * Domain queries deliberately live in the owning finance extension.
 */
public interface StateChangeRepository extends JpaRepository<StateChangeEntity, Long>,
        JpaSpecificationExecutor<StateChangeEntity> {
    @Query("""
            SELECT t FROM StateChangeEntity t
            WHERE t.userId = :userId AND t.transactionType = com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE
              AND t.recordStatus = com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE
              AND (:beforeId IS NULL OR t.id < :beforeId)
            ORDER BY t.id DESC
            """)
    List<StateChangeEntity> findActiveExpensesBefore(@Param("userId") String userId,
                                                     @Param("beforeId") Long beforeId,
                                                     Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM StateChangeEntity t
            WHERE t.id = :id AND t.userId = :userId
              AND t.transactionType = com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE
            """)
    Optional<StateChangeEntity> findExpenseForUpdate(@Param("id") Long id, @Param("userId") String userId);

    @Query(value = """
            SELECT COALESCE(SUM(amount), 0) FROM state_change
            WHERE user_id = :userId AND transaction_type = 'EXPENSE'
              AND record_status = 'ACTIVE'
              AND (LOWER(category) = LOWER(:category) OR LOWER(subcategory) = LOWER(:category))
              AND tx_time >= :start AND tx_time < :end
            """, nativeQuery = true)
    BigDecimal sumExpenseCategory(@Param("userId") String userId, @Param("category") String category,
                                  @Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            SELECT DISTINCT category, subcategory FROM state_change
            WHERE user_id = :userId AND transaction_type = 'EXPENSE'
              AND record_status = 'ACTIVE'
              AND category IS NOT NULL
            ORDER BY category, subcategory
            """, nativeQuery = true)
    List<Object[]> findExpenseScopes(@Param("userId") String userId);

    @Query(value = """
            SELECT COALESCE(SUM(t.amount), 0) FROM state_change t
            LEFT JOIN state_container c ON c.id = t.source_container_id
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
              AND (:sourceAccount IS NULL OR LOWER(c.name) = LOWER(:sourceAccount))
            """, nativeQuery = true)
    BigDecimal sumExpenses(@Param("userId") String userId, @Param("start") Instant start,
                           @Param("end") Instant end, @Param("category") String category,
                           @Param("sourceAccount") String sourceAccount);

    @Query(value = """
            SELECT t.category, COALESCE(SUM(t.amount), 0) FROM state_change t
            LEFT JOIN state_container c ON c.id = t.source_container_id
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time BETWEEN :start AND :end
              AND (:sourceAccount IS NULL OR LOWER(c.name) = LOWER(:sourceAccount))
            GROUP BY t.category ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumByCategory(@Param("userId") String userId, @Param("start") Instant start,
                                 @Param("end") Instant end, @Param("sourceAccount") String sourceAccount);

    @Query(value = """
            SELECT t.subcategory, COALESCE(SUM(t.amount), 0) FROM state_change t
            LEFT JOIN state_container c ON c.id = t.source_container_id
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
              AND (:sourceAccount IS NULL OR LOWER(c.name) = LOWER(:sourceAccount))
            GROUP BY t.subcategory ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumBySubcategory(@Param("userId") String userId, @Param("start") Instant start,
                                    @Param("end") Instant end, @Param("category") String category,
                                    @Param("sourceAccount") String sourceAccount);

    @Query(value = """
            SELECT COALESCE(c.name, 'Unallocated'), COALESCE(SUM(t.amount), 0) FROM state_change t
            LEFT JOIN state_container c ON c.id = t.source_container_id
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time BETWEEN :start AND :end
              AND (:category IS NULL OR t.category = :category)
            GROUP BY c.name ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumBySourceAccount(@Param("userId") String userId, @Param("start") Instant start,
                                      @Param("end") Instant end, @Param("category") String category);
}
