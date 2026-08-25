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
import java.util.List;
import java.util.Map;
import com.apps.deen_sa.finance.budget.BudgetInsightService;
import com.apps.deen_sa.finance.credit.CardDueReminderService;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.presentation.FinancialPresentationRequest;
import com.apps.deen_sa.finance.presentation.VisualizationPlan;
import com.apps.deen_sa.finance.presentation.VisualizationPlanner;
import com.apps.deen_sa.finance.presentation.PresentationAnalyticsService;
import com.apps.deen_sa.finance.presentation.PresentationDataset;

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
    private final PresentationAnalyticsService presentationAnalytics;

    public QueryHandler(
            ExpenseQueryBuilder expenseQueryBuilder,
            ExpenseAnalyticsService expenseAnalyticsService,
            ExpenseSummaryExplainer expenseSummaryExplainer, QueryClassifier queryClassifier,
            QueryContextFormatter queryContextFormatter, BudgetInsightService budgetInsights,
            CardDueReminderService cardReminders, StateContainerRepository stateContainers,
            ExpenseChartRenderer chartRenderer, VisualizationPlanner visualizationPlanner,
            PresentationAnalyticsService presentationAnalytics
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
        this.presentationAnalytics = presentationAnalytics;
    }

    /** Executes the query plan already produced by the unified interpreter with no additional model calls. */
    public SpeechResult handleInterpreted(String period, ConversationContext context) {
        return handleInterpreted(period, null, null, context);
    }

    public SpeechResult handleInterpreted(String period, String analysisIntent, String presentationMood,
                                          ConversationContext context) {
        boolean whatsapp = "WHATSAPP".equalsIgnoreCase(context.getChannel());
        VisualizationPlan plan = visualizationPlanner.plan(
                FinancialPresentationRequest.fromAi(analysisIntent, presentationMood));
        if ("ACCOUNT_BALANCE".equals(period)) {
            if (whatsapp) return portalInsight(accountBalances(context.getUserId()));
            Map<String, BigDecimal> balances = accountBalanceValues(context.getUserId());
            return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                    .message(accountBalances(context.getUserId()))
                    .media(chartRenderer.accountStack("Balances across accounts", balances, plan.mood(), context.getLocale()))
                    .build();
        }
        if ("CURRENT_STATUS".equals(period)) {
            String message = budgetInsights.status(context.getUserId(), context.getTimezone());
            if (whatsapp) return portalInsight(message);
            return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                    .message(message).media(chartRenderer.budgetProgress("Monthly budget progress",
                            budgetInsights.progress(context.getUserId(), context.getTimezone()),
                            plan.mood(), context.getLocale())).build();
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
        PresentationDataset presentation = presentationAnalytics.load(context.getUserId(), query.getTimeRange(),
                plan, context.getTimezone());
        com.apps.deen_sa.llm.AiCallTelemetry.avoided("query_classification_and_explanation");
        if (whatsapp) return portalInsight(highLevelSummary(context.getLocale(), period, summary));
        return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                .message(summary(context.getLocale(), period, summary))
                .media(chartRenderer.render(plan, chartTitle(period), summary, presentation, context.getLocale()))
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
                .media(chartRenderer.render(plan, "Spending by category", summary,
                        PresentationDataset.empty(), ctx.getLocale()))
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
        String periodLabel = switch (period) {
            case "TODAY" -> "today";
            case "THIS_WEEK" -> "this week";
            case "THIS_MONTH" -> "this month";
            case "THIS_YEAR" -> "this year";
            case "LAST_7_DAYS" -> "in the last 7 days";
            default -> "for the requested period";
        };
        StringBuilder result = new StringBuilder(tamil
                ? "💰 *மொத்த செலவு: " + amount + "*"
                : "💰 *Total spent: " + amount + "* " + periodLabel);
        if (summary.getSpendByCategory() != null && !summary.getSpendByCategory().isEmpty()) {
            result.append(tamil ? "\n\n📊 *வகை வாரியான செலவு*" : "\n\n📊 *Category breakdown*");
            summary.getSpendByCategory().entrySet().stream().limit(6).forEach(entry -> {
                BigDecimal value = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                long percentage = total.signum() == 0 ? 0 : value.multiply(BigDecimal.valueOf(100))
                        .divide(total, 0, java.math.RoundingMode.HALF_UP).longValue();
                result.append("\n• ").append(categoryEmoji(entry.getKey())).append(" ")
                        .append(entry.getKey()).append(" — ₹")
                        .append(value.stripTrailingZeros().toPlainString()).append(" · ")
                        .append(percentage).append("%");
            });
        }
        if (summary.getSpendBySubcategory() != null && !summary.getSpendBySubcategory().isEmpty()) {
            List<Map.Entry<String, BigDecimal>> details = summary.getSpendBySubcategory().entrySet().stream()
                    .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank()).limit(5).toList();
            if (!details.isEmpty()) {
                result.append(tamil ? "\n\n🔎 *முக்கிய விவரங்கள்*" : "\n\n🔎 *Top details*");
                details.forEach(entry -> result.append("\n• ").append(entry.getKey()).append(" — ₹")
                        .append(entry.getValue().stripTrailingZeros().toPlainString()));
            }
        }
        return result.toString();
    }

    private String highLevelSummary(String locale, String period, ExpenseSummary summary) {
        BigDecimal total = summary.getTotalSpend() == null ? BigDecimal.ZERO : summary.getTotalSpend();
        String amount = "₹" + total.stripTrailingZeros().toPlainString();
        boolean tamil = locale != null && Set.of("ta", "ta-in").contains(locale.toLowerCase(Locale.ROOT));
        String periodLabel = QueryPeriodLabelFormatter.describe(period);
        return tamil ? "மொத்த செலவு: " + amount : "Total spent: " + amount + " " + periodLabel + ".";
    }

    private SpeechResult portalInsight(String message) {
        return SpeechResult.builder().status(com.apps.deen_sa.conversation.SpeechStatus.INFO)
                .message(message + "\n\nFor detailed breakdowns and more insights, open the portal.")
                .actions(List.of(new com.apps.deen_sa.conversation.ResponseAction(
                        "portal:insights", "Open portal")))
                .build();
    }

    private String categoryEmoji(String category) {
        if (category == null) return "📌";
        String value = category.toLowerCase(Locale.ROOT);
        if (value.contains("food") || value.contains("dining")) return "🍽️";
        if (value.contains("family")) return "👨‍👩‍👧";
        if (value.contains("education")) return "🎓";
        if (value.contains("transport") || value.contains("travel")) return "🚗";
        if (value.contains("medical") || value.contains("health")) return "💊";
        if (value.contains("entertainment")) return "🎬";
        if (value.contains("shopping")) return "🛍️";
        if (value.contains("housing") || value.contains("home")) return "🏠";
        if (value.contains("bill") || value.contains("utilities")) return "💡";
        return "📌";
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
