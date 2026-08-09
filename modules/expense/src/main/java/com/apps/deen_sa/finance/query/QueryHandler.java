package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.dto.ExpenseQuery;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.dto.QueryResult;
import com.apps.deen_sa.llm.impl.ExpenseSummaryExplainer;
import com.apps.deen_sa.llm.impl.QueryClassifier;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.finance.expense.ExpenseAnalyticsService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import com.apps.deen_sa.finance.budget.BudgetInsightService;
import com.apps.deen_sa.finance.credit.CardDueReminderService;

@Service
public class QueryHandler implements SpeechHandler {

    private final ExpenseQueryBuilder expenseQueryBuilder;
    private final ExpenseAnalyticsService expenseAnalyticsService;
    private final ExpenseSummaryExplainer expenseSummaryExplainer;
    private final QueryClassifier queryClassifier;
    private final QueryContextFormatter queryContextFormatter;
    private final BudgetInsightService budgetInsights;
    private final CardDueReminderService cardReminders;

    public QueryHandler(
            ExpenseQueryBuilder expenseQueryBuilder,
            ExpenseAnalyticsService expenseAnalyticsService,
            ExpenseSummaryExplainer expenseSummaryExplainer, QueryClassifier queryClassifier,
            QueryContextFormatter queryContextFormatter, BudgetInsightService budgetInsights,
            CardDueReminderService cardReminders
    ) {
        this.expenseQueryBuilder = expenseQueryBuilder;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.expenseSummaryExplainer = expenseSummaryExplainer;
        this.queryClassifier = queryClassifier;
        this.queryContextFormatter = queryContextFormatter;
        this.budgetInsights = budgetInsights;
        this.cardReminders = cardReminders;
    }

    /** Executes the query plan already produced by the unified interpreter with no additional model calls. */
    public SpeechResult handleInterpreted(String period, ConversationContext context) {
        if ("CURRENT_STATUS".equals(period)) return SpeechResult.info(budgetInsights.status(context.getUserId(), context.getTimezone()));
        if ("UPCOMING_DUE".equals(period)) return SpeechResult.info(cardReminders.reminders(context.getUserId(), context.getTimezone()));
        QueryResult result = new QueryResult();
        result.setIntent("QUERY");
        result.setQueryType("EXPENSE_TOTAL");
        result.setTimePeriod(period);
        result.setIncludeTotal(true);
        result.setGroupByCategory(true);
        ExpenseQuery query = expenseQueryBuilder.from(result, context.getUserId());
        ExpenseSummary summary = expenseAnalyticsService.analyze(query);
        com.apps.deen_sa.llm.AiCallTelemetry.avoided("query_classification_and_explanation");
        return SpeechResult.info(summary(context.getLocale(), period, summary));
    }

    @Override
    public SpeechResult handleSpeech(String userText, ConversationContext ctx) {
        QueryResult result = queryClassifier.classify(userText);

        ExpenseQuery query = expenseQueryBuilder.from(result, ctx.getUserId());
        ExpenseSummary summary = expenseAnalyticsService.analyze(query);

        String context =
                queryContextFormatter.describe(result);

        String response =
                expenseSummaryExplainer.explain(summary, userText, context);

        return SpeechResult.info(response);
    }

    @Override
    public String intentType() {
        return "QUERY";
    }

    @Override
    public SpeechResult handleFollowup(String userAnswer, ConversationContext ctx) {
        return SpeechResult.info("No follow-ups supported for queries yet.");
    }

    private String summary(String locale, String period, ExpenseSummary summary) {
        BigDecimal total = summary.getTotalSpend() == null ? BigDecimal.ZERO : summary.getTotalSpend();
        String amount = "₹" + total.stripTrailingZeros().toPlainString();
        boolean tamil = locale != null && Set.of("ta", "ta-in").contains(locale.toLowerCase(Locale.ROOT));
        if (tamil) return switch (period) {
            case "TODAY" -> "இன்று உங்கள் மொத்த செலவு " + amount + ".";
            case "THIS_MONTH" -> "இந்த மாதம் உங்கள் மொத்த செலவு " + amount + ".";
            default -> "கேட்ட காலத்தில் உங்கள் மொத்த செலவு " + amount + ".";
        };
        String result = "You spent a total of " + amount + " " + switch (period) {
            case "TODAY" -> "today";
            case "THIS_WEEK" -> "this week";
            case "THIS_MONTH" -> "this month";
            case "THIS_YEAR" -> "this year";
            default -> "for the requested period";
        } + ".";
        if (summary.getSpendByCategory() != null && !summary.getSpendByCategory().isEmpty()) {
            String breakdown = summary.getSpendByCategory().entrySet().stream()
                    .map(entry -> entry.getKey() + " ₹" + entry.getValue().stripTrailingZeros().toPlainString())
                    .collect(Collectors.joining(", "));
            result += " Category breakdown: " + breakdown + ".";
        }
        return result;
    }
}
