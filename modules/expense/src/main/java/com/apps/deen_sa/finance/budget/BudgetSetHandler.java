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
    private static final String CONFIRM_BUDGET = "confirmBudget";
    private final MonthlyBudgetRepository budgets;
    private final com.apps.deen_sa.finance.expense.ExpenseCategoryResolver categories;
    private final com.apps.deen_sa.finance.legacy.state.StateChangeRepository expenses;
    public BudgetSetHandler(MonthlyBudgetRepository budgets,
                            com.apps.deen_sa.finance.expense.ExpenseCategoryResolver categories,
                            com.apps.deen_sa.finance.legacy.state.StateChangeRepository expenses) {
        this.budgets = budgets; this.categories = categories; this.expenses = expenses;
    }
    @Override public String intentType() { return "BUDGET_SET"; }
    @Override public SpeechResult handleSpeech(String text, ConversationContext context) {
        return SpeechResult.invalid("Budgets must be supplied through structured interpretation.");
    }
    @Override public SpeechResult handleFollowup(String answer, ConversationContext context) {
        return CONFIRM_BUDGET.equals(context.getWaitingForField())
                ? confirm(answer, context)
                : SpeechResult.invalid("Please state the category and monthly budget together.");
    }
    @Override @Transactional
    public SpeechResult handleInterpreted(EventPatch event, String rawText, ConversationContext context) {
        if (CONFIRM_BUDGET.equals(context.getWaitingForField())) return confirm(rawText, context);
        Object rawAmount = event.fields().asMap().get("amount");
        String proposed = categories.resolveBudgetScope(text(event.fields().asMap().get("category")), rawText).orElse(null);
        if (proposed == null || proposed.isBlank())
            return SpeechResult.followup("Which category is this monthly budget for?", List.of("category"), event);
        if (rawAmount == null) return SpeechResult.followup("What is the monthly budget amount?", List.of("amount"), event);
        BigDecimal amount = new BigDecimal(rawAmount.toString());
        if (amount.signum() <= 0) return SpeechResult.invalid("A monthly budget must be greater than zero.");

        BudgetPreview preview = userScope(context.getUserId(), proposed, amount);
        if (preview == null) {
            context.reset();
            return SpeechResult.info("I couldn't find " + proposed + " in your confirmed expense history. "
                    + "Add at least one expense in that category or subcategory before creating this budget.");
        }
        context.setActiveIntent("BUDGET_SET");
        context.setWaitingForField(CONFIRM_BUDGET);
        context.setPartialObject(preview);
        return SpeechResult.followup("I identified:\n"
                        + "Monthly budget: ₹" + money(amount) + "\n"
                        + "Category: " + preview.category() + "\n"
                        + "Subcategory: " + value(preview.subcategory()) + "\n"
                        + "Budget scope: " + preview.scope() + "\n\nConfirm this budget?",
                List.of(CONFIRM_BUDGET), preview, List.of(
                        new ResponseAction("answer:CONFIRM_BUDGET", "Confirm"),
                        new ResponseAction("answer:DISCARD_BUDGET", "Discard")));
    }

    private SpeechResult confirm(String answer, ConversationContext context) {
        if ("DISCARD_BUDGET".equalsIgnoreCase(answer)) {
            context.reset();
            return SpeechResult.info("Discarded. No budget was saved.");
        }
        if (!"CONFIRM_BUDGET".equalsIgnoreCase(answer)) {
            return SpeechResult.invalid("Choose Confirm or Discard.");
        }
        BudgetPreview preview = (BudgetPreview) context.getPartialObject();
        Instant now = Instant.now();
        MonthlyBudgetEntity budget = budgets.findByUserIdAndCategoryIgnoreCase(context.getUserId(), preview.scope())
                .orElseGet(MonthlyBudgetEntity::new);
        if (budget.getId() == null) { budget.setUserId(context.getUserId()); budget.setCreatedAt(now); }
        budget.setCategory(preview.scope()); budget.setMonthlyLimit(preview.amount()); budget.setActive(true); budget.setUpdatedAt(now);
        MonthlyBudgetEntity saved = budgets.save(budget);
        context.reset();
        return SpeechResult.builder().status(SpeechStatus.SAVED).savedEntity(saved).needFollowup(false)
                .message("Set your monthly " + saved.getCategory() + " budget to ₹" + money(preview.amount()) + ".").build();
    }

    private BudgetPreview userScope(Long userId, String proposed, BigDecimal amount) {
        List<Object[]> scopes = expenses.findExpenseScopes(String.valueOf(userId));
        Object[] subcategory = scopes.stream()
                .filter(row -> equals(row[1], proposed)).findFirst().orElse(null);
        if (subcategory != null) return new BudgetPreview(text(subcategory[0]), text(subcategory[1]),
                text(subcategory[1]), amount);
        Object[] category = scopes.stream()
                .filter(row -> equals(row[0], proposed)).findFirst().orElse(null);
        return category == null ? null : new BudgetPreview(text(category[0]), null, text(category[0]), amount);
    }
    private boolean equals(Object value, String expected) { return value != null && value.toString().equalsIgnoreCase(expected); }
    private String value(String value) { return value == null || value.isBlank() ? "null" : value; }
    private String text(Object value) { return value == null ? null : value.toString(); }
    private String money(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    public record BudgetPreview(String category, String subcategory, String scope, BigDecimal amount) { }
}
