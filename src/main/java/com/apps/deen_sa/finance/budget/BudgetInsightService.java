package com.apps.deen_sa.finance.budget;

import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class BudgetInsightService {
    private final MonthlyBudgetRepository budgets;
    private final StateChangeRepository changes;
    public BudgetInsightService(MonthlyBudgetRepository budgets, StateChangeRepository changes) {
        this.budgets = budgets; this.changes = changes;
    }

    public String status(Long userId, String timezone) {
        List<MonthlyBudgetEntity> active = budgets.findByUserIdAndActiveTrueOrderByCategoryAsc(userId);
        if (active.isEmpty()) return "You have no active monthly budgets. Try: Set my monthly groceries budget to ₹10,000.";
        ZoneId zone = zone(timezone); YearMonth month = YearMonth.now(zone);
        return active.stream().map(budget -> line(budget, spend(userId, budget.getCategory(), month, zone))).reduce((a, b) -> a + "\n" + b).orElseThrow();
    }

    public List<BudgetProgress> progress(Long userId, String timezone) {
        ZoneId zone = zone(timezone); YearMonth month = YearMonth.now(zone);
        return budgets.findByUserIdAndActiveTrueOrderByCategoryAsc(userId).stream()
                .map(budget -> new BudgetProgress(budget.getCategory(),
                        spend(userId, budget.getCategory(), month, zone), budget.getMonthlyLimit()))
                .toList();
    }

    public Optional<String> alert(StateChangeEntity expense, String timezone) {
        if (expense == null || expense.getCategory() == null || expense.getTimestamp() == null) return Optional.empty();
        Long userId = Long.valueOf(expense.getUserId());
        MonthlyBudgetEntity budget = (expense.getSubcategory() == null ? Optional.<MonthlyBudgetEntity>empty()
                : budgets.findByUserIdAndCategoryIgnoreCase(userId, expense.getSubcategory()))
                .or(() -> budgets.findByUserIdAndCategoryIgnoreCase(userId, expense.getCategory()))
                .filter(MonthlyBudgetEntity::isActive).orElse(null);
        if (budget == null) return Optional.empty();
        ZoneId zone = zone(timezone); YearMonth month = YearMonth.from(expense.getTimestamp().atZone(zone));
        BigDecimal spent = spend(userId, budget.getCategory(), month, zone);
        BigDecimal ratio = spent.divide(budget.getMonthlyLimit(), 4, java.math.RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) > 0) return Optional.of("Budget alert: " + budget.getCategory()
                + " is ₹" + money(spent.subtract(budget.getMonthlyLimit())) + " over its ₹" + money(budget.getMonthlyLimit()) + " monthly budget.");
        if (ratio.compareTo(new BigDecimal("0.80")) >= 0) return Optional.of("Budget alert: you have used "
                + ratio.multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP) + "% of your "
                + budget.getCategory() + " budget; ₹" + money(budget.getMonthlyLimit().subtract(spent)) + " remaining.");
        return Optional.empty();
    }

    private String line(MonthlyBudgetEntity budget, BigDecimal spent) {
        BigDecimal remaining = budget.getMonthlyLimit().subtract(spent);
        if (remaining.signum() < 0) return budget.getCategory() + ": spent ₹" + money(spent) + " of ₹"
                + money(budget.getMonthlyLimit()) + " — over budget by ₹" + money(remaining.abs()) + ".";
        return budget.getCategory() + ": spent ₹" + money(spent) + " of ₹" + money(budget.getMonthlyLimit())
                + " — ₹" + money(remaining) + " remaining.";
    }
    private BigDecimal spend(Long userId, String category, YearMonth month, ZoneId zone) {
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        BigDecimal value = changes.sumExpenseCategory(String.valueOf(userId), category, start, end);
        return value == null ? BigDecimal.ZERO : value;
    }
    private ZoneId zone(String value) { try { return ZoneId.of(value); } catch (Exception ignored) { return ZoneId.of("Asia/Kolkata"); } }
    private String money(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
