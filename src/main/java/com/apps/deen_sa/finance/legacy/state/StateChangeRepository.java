package com.apps.deen_sa.finance.legacy.state;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public interface StateChangeRepository extends JpaRepository<StateChangeEntity, Long> {
    @Query("SELECT t FROM StateChangeEntity t WHERE t.id=:id AND t.userId=:userId AND t.transactionType=com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StateChangeEntity> findExpenseForUpdate(@Param("id") Long id, @Param("userId") String userId);

    @Query("SELECT t FROM StateChangeEntity t WHERE t.userId=:userId AND t.transactionType=com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE AND t.recordStatus=com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE AND t.timestamp>=:start AND t.timestamp<:end AND (:categoryFiltered=false OR LOWER(t.category)=LOWER(:category)) AND (:subcategoryFiltered=false OR LOWER(t.subcategory)=LOWER(:subcategory)) AND (:beforeId IS NULL OR t.id<:beforeId) ORDER BY t.id DESC")
    List<StateChangeEntity> findFilteredActiveExpensesBefore(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end,@Param("categoryFiltered") boolean categoryFiltered,@Param("category") String category,@Param("subcategoryFiltered") boolean subcategoryFiltered,@Param("subcategory") String subcategory,@Param("beforeId") Long beforeId,Pageable pageable);

    @Query("SELECT COUNT(t),COALESCE(SUM(t.amount),0) FROM StateChangeEntity t WHERE t.userId=:userId AND t.transactionType=com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE AND t.recordStatus=com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE AND t.timestamp>=:start AND t.timestamp<:end AND (:categoryFiltered=false OR LOWER(t.category)=LOWER(:category)) AND (:subcategoryFiltered=false OR LOWER(t.subcategory)=LOWER(:subcategory))")
    List<Object[]> summarizeFilteredActiveExpenses(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end,@Param("categoryFiltered") boolean categoryFiltered,@Param("category") String category,@Param("subcategoryFiltered") boolean subcategoryFiltered,@Param("subcategory") String subcategory);

    @Query(value="SELECT t.* FROM state_change t WHERE t.user_id=:userId AND t.transaction_type='EXPENSE' AND t.record_status='ACTIVE' AND t.tx_time>=:start AND t.tx_time<:end AND (:categoryFiltered=false OR LOWER(t.category)=LOWER(:category)) AND (:subcategoryFiltered=false OR LOWER(t.subcategory)=LOWER(:subcategory)) AND EXISTS (SELECT 1 FROM transaction_tag tt WHERE tt.transaction_id=t.id AND tt.tag_id IN (:tagIds)) AND (:beforeId IS NULL OR t.id<:beforeId) ORDER BY t.id DESC",nativeQuery=true)
    List<StateChangeEntity> findTagFilteredActiveExpensesBefore(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end,@Param("categoryFiltered") boolean categoryFiltered,@Param("category") String category,@Param("subcategoryFiltered") boolean subcategoryFiltered,@Param("subcategory") String subcategory,@Param("tagIds") List<Long> tagIds,@Param("beforeId") Long beforeId,Pageable pageable);

    @Query(value="SELECT COUNT(t.id),COALESCE(SUM(t.amount),0) FROM state_change t WHERE t.user_id=:userId AND t.transaction_type='EXPENSE' AND t.record_status='ACTIVE' AND t.tx_time>=:start AND t.tx_time<:end AND (:categoryFiltered=false OR LOWER(t.category)=LOWER(:category)) AND (:subcategoryFiltered=false OR LOWER(t.subcategory)=LOWER(:subcategory)) AND EXISTS (SELECT 1 FROM transaction_tag tt WHERE tt.transaction_id=t.id AND tt.tag_id IN (:tagIds))",nativeQuery=true)
    List<Object[]> summarizeTagFilteredActiveExpenses(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end,@Param("categoryFiltered") boolean categoryFiltered,@Param("category") String category,@Param("subcategoryFiltered") boolean subcategoryFiltered,@Param("subcategory") String subcategory,@Param("tagIds") List<Long> tagIds);

    @Query("SELECT COUNT(t),COALESCE(SUM(t.amount),0) FROM StateChangeEntity t WHERE t.userId=:userId AND t.transactionType=com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum.EXPENSE AND t.recordStatus=com.apps.deen_sa.finance.expense.ExpenseRecordStatus.ACTIVE AND t.timestamp>=:start AND t.timestamp<:end")
    List<Object[]> summarizeActiveExpensesForPeriod(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end);
    @Query(value="SELECT COALESCE(category,'Uncategorized'),COALESCE(SUM(amount),0) FROM state_change WHERE user_id=:userId AND transaction_type='EXPENSE' AND record_status='ACTIVE' AND tx_time>=:start AND tx_time<:end GROUP BY 1 ORDER BY 2 DESC",nativeQuery=true)
    List<Object[]> sumExpensesByCategoryForPeriod(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end);
    @Query(value="SELECT COUNT(*) FROM state_change WHERE user_id=:userId AND transaction_type='EXPENSE' AND record_status='ACTIVE' AND tx_time>=:start AND tx_time<:end",nativeQuery=true)
    long countExpensesForPeriod(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end);
    @Query(value="SELECT COALESCE(SUM(amount),0) FROM state_change WHERE user_id=:userId AND transaction_type='EXPENSE' AND record_status='ACTIVE' AND tx_time BETWEEN :start AND :end AND (:category IS NULL OR LOWER(category)=LOWER(:category))",nativeQuery=true)
    BigDecimal sumExpenses(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end,@Param("category") String category);
    @Query(value="SELECT category,COALESCE(SUM(amount),0) FROM state_change WHERE user_id=:userId AND transaction_type='EXPENSE' AND record_status='ACTIVE' AND tx_time BETWEEN :start AND :end GROUP BY category ORDER BY 2 DESC",nativeQuery=true)
    List<Object[]> sumByCategory(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end);
    @Query(value="SELECT subcategory,COALESCE(SUM(amount),0) FROM state_change WHERE user_id=:userId AND transaction_type='EXPENSE' AND record_status='ACTIVE' AND tx_time BETWEEN :start AND :end AND (:category IS NULL OR LOWER(category)=LOWER(:category)) GROUP BY subcategory ORDER BY 2 DESC",nativeQuery=true)
    List<Object[]> sumBySubcategory(@Param("userId") String userId,@Param("start") Instant start,@Param("end") Instant end,@Param("category") String category);
    @Query(value="SELECT TO_CHAR((tx_time AT TIME ZONE 'UTC') AT TIME ZONE :timezone,'YYYY-MM-DD'), COUNT(*), COALESCE(SUM(amount),0) FROM state_change WHERE user_id=:userId AND transaction_type='EXPENSE' AND record_status='ACTIVE' AND tx_time>=:start AND tx_time<:end GROUP BY 1 ORDER BY 1",nativeQuery=true)
    List<Object[]> summarizeExpensesByLocalDay(@Param("userId") String userId, @Param("start") Instant start,
                                                @Param("end") Instant end, @Param("timezone") String timezone);
}
