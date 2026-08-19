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
import java.util.LinkedHashMap;
import java.util.Map;
import com.apps.deen_sa.finance.budget.BudgetInsightService;
import com.apps.deen_sa.finance.credit.CardDueReminderService;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.presentation.FinancialPresentationRequest;
import com.apps.deen_sa.finance.presentation.VisualizationPlan;
import com.apps.deen_sa.finance.presentation.VisualizationPlanner;

@Service
public class QueryHandler implements SpeechHandler {

    private final ExpenseQueryBuilder expenseQueryBuilder;
    private final ExpenseAnalyticsService expenseAnalyticsService;
    private final ExpenseSummaryExplainer expenseSummaryExplainer;
    private final QueryClassifier queryClassifier;
    private final QueryContextFormatter queryContextFormatter;
    private final BudgetInsightService budgetInsights;
    private final CardDueReminderService cardReminders;
    private final StateContainerRepository stateContainers;
    private final ExpenseChartRenderer chartRenderer;
    private final VisualizationPlanner visualizationPlanner;

    public QueryHandler(
            ExpenseQueryBuilder expenseQueryBuilder,
            ExpenseAnalyticsService expenseAnalyticsService,
            ExpenseSummaryExplainer expenseSummaryExplainer, QueryClassifier queryClassifier,
            QueryContextFormatter queryContextFormatter, BudgetInsightService budgetInsights,
            CardDueReminderService cardReminders, StateContainerRepository stateContainers,
            ExpenseChartRenderer chartRenderer, VisualizationPlanner visualizationPlanner
    ) {
        this.expenseQueryBuilder = expenseQueryBuilder;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.expenseSummaryExplainer = expenseSummaryExplainer;
        this.queryClassifier = queryClassifier;
        this.queryContextFormatter = queryContextFormatter;
        this.budgetInsights = budgetInsights;
        this.cardReminders = cardReminders;
        this.stateContainers = stateContainers;
        this.chartRenderer = chartRenderer;
        this.visualizationPlanner = visualizationPlanner;
    }

    /** Executes the query plan already produced by the unified interpreter with no additional model calls. */
    public SpeechResult handleInterpreted(String period, ConversationContext context) {
        return handleInterpreted(period, null, null, context);
    }

    public SpeechResult handleInterpreted(String period, String analysisIntent, String presentationMood,
                                          ConversationContext context) {
        VisualizationPlan plan = visualizationPlanner.plan(
                FinancialPresentationRequest.fromAi(analysisIntent, presentationMood));
        if ("ACCOUNT_BALANCE".equals(period)) {
            Map<String, BigDecimal> balances = accountBalanceValues(context.getUserId());
            return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                    .message(accountBalances(context.getUserId()))
                    .media(chartRenderer.accountStack("Balances across accounts", balances, plan.mood(), context.getLocale()))
                    .build();
        }
        if ("CURRENT_STATUS".equals(period)) {
            String message = budgetInsights.status(context.getUserId(), context.getTimezone());
            return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                    .message(message)
                    .media(chartRenderer.budgetProgress("Monthly budget progress",
                            budgetInsights.progress(context.getUserId(), context.getTimezone()),
                            plan.mood(), context.getLocale()))
                    .build();
        }
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
        String message = summary(context.getLocale(), period, summary);
        return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                .message(message)
                .media(chartRenderer.render(plan, chartTitle(period), summary, context.getLocale()))
                .build();
    }

    private String accountBalances(Long userId) {
        var accounts = stateContainers.findActiveByOwnerId(userId).stream()
                .filter(account -> Set.of("BANK_ACCOUNT", "CASH", "WALLET", "CREDIT_CARD")
                        .contains(account.getContainerType()))
                .toList();
        if (accounts.isEmpty()) return "You have no active financial accounts yet.";
        return accounts.stream().map(this::balanceLine).collect(Collectors.joining("\n"));
    }

    private Map<String, BigDecimal> accountBalanceValues(Long userId) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        stateContainers.findActiveByOwnerId(userId).stream()
                .filter(account -> Set.of("BANK_ACCOUNT", "CASH", "WALLET", "CREDIT_CARD")
                        .contains(account.getContainerType()))
                .filter(account -> account.getCurrentValue() != null)
                .forEach(account -> values.put(account.getName(), account.getCurrentValue()));
        return values;
    }

    private String balanceLine(StateContainerEntity account) {
        if (account.getCurrentValue() == null) return account.getName() + " balance is unknown.";
        String amount = account.getCurrentValue().stripTrailingZeros().toPlainString();
        if ("CREDIT_CARD".equals(account.getContainerType()))
            return account.getName() + " outstanding balance is ₹" + amount + ".";
        return account.getName() + " balance is ₹" + amount + ".";
    }

    @Override
    public SpeechResult handleSpeech(String userText, ConversationContext ctx) {
        QueryResult result = queryClassifier.classify(userText);

        ExpenseQuery query = expenseQueryBuilder.from(result, ctx.getUserId());
        ExpenseSummary summary = expenseAnalyticsService.analyze(query);
        VisualizationPlan plan = visualizationPlanner.plan(FinancialPresentationRequest.fromAi(null, null));

        String context =
                queryContextFormatter.describe(result);

        String response =
                expenseSummaryExplainer.explain(summary, userText, context);

        return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                .message(response)
                .media(chartRenderer.render(plan, "Spending by category", summary, ctx.getLocale()))
                .build();
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
            case "LAST_7_DAYS" -> "in the last 7 days";
            default -> "for the requested period";
        } + ".";
        if (summary.getSpendByCategory() != null && !summary.getSpendByCategory().isEmpty()) {
            String breakdown = summary.getSpendByCategory().entrySet().stream()
                    .map(entry -> entry.getKey() + " ₹" + entry.getValue().stripTrailingZeros().toPlainString())
                    .collect(Collectors.joining(", "));
            result += " Category breakdown: " + breakdown + ".";
        }
        if (summary.getSpendBySubcategory() != null && !summary.getSpendBySubcategory().isEmpty()) {
            String breakdown = summary.getSpendBySubcategory().entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .map(entry -> entry.getKey() + " ₹" + entry.getValue().stripTrailingZeros().toPlainString())
                    .collect(Collectors.joining(", "));
            if (!breakdown.isBlank()) result += " Details: " + breakdown + ".";
        }
        return result;
    }

    private String chartTitle(String period) {
        return switch (period) {
            case "TODAY" -> "Today's spending by category";
            case "THIS_WEEK" -> "This week's spending by category";
            case "THIS_MONTH" -> "This month's spending by category";
            case "THIS_YEAR" -> "This year's spending by category";
            case "LAST_MONTH" -> "Last month's spending by category";
            case "LAST_7_DAYS" -> "Last 7 days' spending by category";
            case "LAST_3_MONTHS" -> "Last 3 months' spending by category";
            default -> "Spending by category";
        };
    }
}
