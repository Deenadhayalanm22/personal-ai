package com.apps.deen_sa.finance.budget;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.conversation.interpretation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class BudgetSetHandler implements StructuredEventHandler {
    private final MonthlyBudgetRepository budgets;
    private final com.apps.deen_sa.finance.expense.ExpenseCategoryResolver categories;
    public BudgetSetHandler(MonthlyBudgetRepository budgets,
                            com.apps.deen_sa.finance.expense.ExpenseCategoryResolver categories) {
        this.budgets = budgets; this.categories = categories;
    }
    @Override public String intentType() { return "BUDGET_SET"; }
    @Override public SpeechResult handleSpeech(String text, ConversationContext context) {
        return SpeechResult.invalid("Budgets must be supplied through structured interpretation.");
    }
    @Override public SpeechResult handleFollowup(String answer, ConversationContext context) {
        return SpeechResult.invalid("Please state the category and monthly budget together.");
    }
    @Override @Transactional
    public SpeechResult handleInterpreted(EventPatch event, String rawText, ConversationContext context) {
        Object rawAmount = event.fields().asMap().get("amount");
        String category = categories.resolveBudgetScope(text(event.fields().asMap().get("category")), rawText).orElse(null);
        if (category == null || category.isBlank())
            return SpeechResult.followup("Which category is this monthly budget for?", List.of("category"), event);
        if (rawAmount == null) return SpeechResult.followup("What is the monthly budget amount?", List.of("amount"), event);
        BigDecimal amount = new BigDecimal(rawAmount.toString());
        if (amount.signum() <= 0) return SpeechResult.invalid("A monthly budget must be greater than zero.");
        Instant now = Instant.now();
        MonthlyBudgetEntity budget = budgets.findByUserIdAndCategoryIgnoreCase(context.getUserId(), category)
                .orElseGet(MonthlyBudgetEntity::new);
        if (budget.getId() == null) { budget.setUserId(context.getUserId()); budget.setCreatedAt(now); }
        budget.setCategory(category.trim()); budget.setMonthlyLimit(amount); budget.setActive(true); budget.setUpdatedAt(now);
        MonthlyBudgetEntity saved = budgets.save(budget);
        context.reset();
        return SpeechResult.builder().status(SpeechStatus.SAVED).savedEntity(saved).needFollowup(false)
                .message("Set your monthly " + saved.getCategory() + " budget to ₹" + money(amount) + ".").build();
    }
    private String text(Object value) { return value == null ? null : value.toString(); }
    private String money(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
