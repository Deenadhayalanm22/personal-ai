package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.budget.MonthlyBudgetEntity;
import com.apps.deen_sa.finance.budget.MonthlyBudgetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class WebBudgetService {
    private final MonthlyBudgetRepository budgets;

    public WebBudgetService(MonthlyBudgetRepository budgets) { this.budgets = budgets; }

    public List<BudgetItem> list(Long userId) {
        return budgets.findByUserIdAndActiveTrueOrderByCategoryAsc(userId).stream().map(this::item).toList();
    }

    public BudgetItem save(Long userId, BudgetUpdate request) {
        if (request == null || request.category() == null || request.category().isBlank()
                || request.monthlyLimit() == null || request.monthlyLimit().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Category and a monthly limit greater than zero are required");
        String category = request.category().trim();
        Instant now = Instant.now();
        MonthlyBudgetEntity value = budgets.findByUserIdAndCategoryIgnoreCase(userId, category)
                .orElseGet(MonthlyBudgetEntity::new);
        if (value.getId() == null) { value.setUserId(userId); value.setCreatedAt(now); }
        value.setCategory(category); value.setMonthlyLimit(request.monthlyLimit());
        value.setActive(true); value.setUpdatedAt(now);
        return item(budgets.save(value));
    }

    public void deactivate(Long userId, Long id) {
        MonthlyBudgetEntity value = budgets.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        value.setActive(false); value.setUpdatedAt(Instant.now()); budgets.save(value);
    }

    private BudgetItem item(MonthlyBudgetEntity value) {
        return new BudgetItem(value.getId(), value.getCategory(), value.getMonthlyLimit(), value.isActive());
    }

    public record BudgetUpdate(String category, BigDecimal monthlyLimit) { }
    public record BudgetItem(Long id, String category, BigDecimal monthlyLimit, boolean active) { }
}
