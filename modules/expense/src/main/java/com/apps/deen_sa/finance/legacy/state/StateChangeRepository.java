package com.apps.deen_sa.finance.legacy.state;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Legacy state-store port retained while old finance rows are projected into the generic ledger.
 * Domain queries deliberately live in the owning finance extension.
 */
public interface StateChangeRepository extends JpaRepository<StateChangeEntity, Long>,
        JpaSpecificationExecutor<StateChangeEntity> {
    @Query(value = """
            SELECT COALESCE(SUM(amount), 0) FROM state_change
            WHERE user_id = :userId AND transaction_type = 'EXPENSE'
              AND (LOWER(category) = LOWER(:category) OR LOWER(subcategory) = LOWER(:category))
              AND tx_time >= :start AND tx_time < :end
            """, nativeQuery = true)
    BigDecimal sumExpenseCategory(@Param("userId") String userId, @Param("category") String category,
                                  @Param("start") Instant start, @Param("end") Instant end);
}
