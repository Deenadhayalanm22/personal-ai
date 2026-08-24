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

    @Query("""
            SELECT t FROM StateChangeEntity t
            WHERE t.userId = :userId
              AND t.transactionType = com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE
              AND t.recordStatus = com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE
              AND t.timestamp >= :start AND t.timestamp < :end
              AND (:beforeId IS NULL OR t.id < :beforeId)
            ORDER BY t.id DESC
            """)
    List<StateChangeEntity> findActiveExpensesForPeriodBefore(
            @Param("userId") String userId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("""
            SELECT t FROM StateChangeEntity t
            WHERE t.userId = :userId
              AND t.transactionType = com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE
              AND t.recordStatus = com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE
              AND t.timestamp >= :start AND t.timestamp < :end
              AND (:accountId IS NULL OR t.sourceContainerId = :accountId OR t.targetContainerId = :accountId)
              AND (:category IS NULL OR LOWER(t.category) = LOWER(:category))
              AND (:subcategory IS NULL OR LOWER(t.subcategory) = LOWER(:subcategory))
              AND (:beforeId IS NULL OR t.id < :beforeId)
            ORDER BY t.id DESC
            """)
    List<StateChangeEntity> findFilteredActiveExpensesBefore(
            @Param("userId") String userId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("accountId") Long accountId,
            @Param("category") String category, @Param("subcategory") String subcategory,
            @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("""
            SELECT COUNT(t), COALESCE(SUM(t.amount), 0) FROM StateChangeEntity t
            WHERE t.userId = :userId
              AND t.transactionType = com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE
              AND t.recordStatus = com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE
              AND t.timestamp >= :start AND t.timestamp < :end
              AND (:accountId IS NULL OR t.sourceContainerId = :accountId OR t.targetContainerId = :accountId)
              AND (:category IS NULL OR LOWER(t.category) = LOWER(:category))
              AND (:subcategory IS NULL OR LOWER(t.subcategory) = LOWER(:subcategory))
            """)
    List<Object[]> summarizeFilteredActiveExpenses(
            @Param("userId") String userId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("accountId") Long accountId,
            @Param("category") String category, @Param("subcategory") String subcategory);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM StateChangeEntity t
            WHERE t.id = :id AND t.userId = :userId
              AND t.transactionType = com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE
            """)
    Optional<StateChangeEntity> findExpenseForUpdate(@Param("id") Long id, @Param("userId") String userId);

    @Query(value = """
            SELECT c.id FROM state_change t
            JOIN state_container c ON c.id = t.source_container_id
            WHERE t.user_id = :userId
              AND t.transaction_type = 'EXPENSE'
              AND t.record_status = 'ACTIVE'
              AND c.status = 'ACTIVE'
              AND c.container_type = :containerType
            ORDER BY t.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findMostRecentlyUsedActiveSourceId(
            @Param("userId") String userId, @Param("containerType") String containerType);

    @Query(value = """
            SELECT activity.account_id, COUNT(DISTINCT activity.transaction_id)
            FROM (
                SELECT id AS transaction_id, source_container_id AS account_id
                FROM state_change
                WHERE user_id = :userId AND record_status = 'ACTIVE'
                  AND tx_time >= :start AND tx_time < :end
                UNION ALL
                SELECT id AS transaction_id, target_container_id AS account_id
                FROM state_change
                WHERE user_id = :userId AND record_status = 'ACTIVE'
                  AND tx_time >= :start AND tx_time < :end
            ) activity
            WHERE activity.account_id IS NOT NULL
            GROUP BY activity.account_id
            """, nativeQuery = true)
    List<Object[]> countActiveTransactionsByAccount(@Param("userId") String userId,
                                                     @Param("start") Instant start,
                                                     @Param("end") Instant end);

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
            SELECT COALESCE(t.category, 'Uncategorized'), COALESCE(SUM(t.amount), 0)
            FROM state_change t
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time >= :start AND t.tx_time < :end
            GROUP BY 1 ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumExpensesByCategoryForPeriod(@Param("userId") String userId,
                                                   @Param("start") Instant start,
                                                   @Param("end") Instant end);

    @Query(value = """
            SELECT COUNT(*) FROM state_change t
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time >= :start AND t.tx_time < :end
            """, nativeQuery = true)
    long countExpensesForPeriod(@Param("userId") String userId,
                                @Param("start") Instant start, @Param("end") Instant end);

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

    @Query(value = """
            SELECT TO_CHAR((t.tx_time AT TIME ZONE 'UTC') AT TIME ZONE :timezone, 'YYYY-MM-DD'),
                   COALESCE(SUM(t.amount), 0)
            FROM state_change t
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time >= :start AND t.tx_time < :end
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> sumExpensesByLocalDay(@Param("userId") String userId, @Param("start") Instant start,
                                         @Param("end") Instant end, @Param("timezone") String timezone);

    @Query(value = """
            SELECT COALESCE(t.category, 'Uncategorized'), COALESCE(t.subcategory, 'Other'),
                   COALESCE(t.main_entity, 'Other'), COALESCE(SUM(t.amount), 0)
            FROM state_change t
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time >= :start AND t.tx_time < :end
            GROUP BY 1, 2, 3 ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumExpenseHierarchy(@Param("userId") String userId, @Param("start") Instant start,
                                       @Param("end") Instant end);

    @Query(value = """
            SELECT COALESCE(c.name, 'Unallocated'), COALESCE(t.category, 'Uncategorized'), COALESCE(SUM(t.amount), 0)
            FROM state_change t LEFT JOIN state_container c ON c.id = t.source_container_id
            WHERE t.user_id = :userId AND t.transaction_type = 'EXPENSE' AND t.record_status = 'ACTIVE'
              AND t.tx_time >= :start AND t.tx_time < :end
            GROUP BY 1, 2 ORDER BY SUM(t.amount) DESC
            """, nativeQuery = true)
    List<Object[]> sumExpenseFlow(@Param("userId") String userId, @Param("start") Instant start,
                                  @Param("end") Instant end);

    @Query(value = """
            SELECT COALESCE(SUM(t.amount), 0) FROM state_change t
            WHERE t.user_id = :userId AND t.transaction_type = 'INCOME' AND t.record_status = 'ACTIVE'
              AND t.tx_time >= :start AND t.tx_time < :end
            """, nativeQuery = true)
    BigDecimal sumIncome(@Param("userId") String userId, @Param("start") Instant start, @Param("end") Instant end);
}
