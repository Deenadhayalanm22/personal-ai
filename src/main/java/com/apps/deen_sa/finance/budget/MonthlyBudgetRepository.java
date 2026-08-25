package com.apps.deen_sa.finance.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudgetEntity, Long> {
    Optional<MonthlyBudgetEntity> findByUserIdAndCategoryIgnoreCase(Long userId, String category);
    Optional<MonthlyBudgetEntity> findByIdAndUserId(Long id, Long userId);
    List<MonthlyBudgetEntity> findByUserIdAndActiveTrueOrderByCategoryAsc(Long userId);
}
