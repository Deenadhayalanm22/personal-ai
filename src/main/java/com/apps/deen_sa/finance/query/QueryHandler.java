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
import java.util.List;
import java.util.Map;

@Service
public class QueryHandler implements SpeechHandler {

    private final ExpenseQueryBuilder expenseQueryBuilder;
    private final ExpenseAnalyticsService expenseAnalyticsService;
    private final ExpenseSummaryExplainer expenseSummaryExplainer;
    private final QueryClassifier queryClassifier;
    private final QueryContextFormatter queryContextFormatter;

    public QueryHandler(
            ExpenseQueryBuilder expenseQueryBuilder,
            ExpenseAnalyticsService expenseAnalyticsService,
            ExpenseSummaryExplainer expenseSummaryExplainer, QueryClassifier queryClassifier,
            QueryContextFormatter queryContextFormatter
    ) {
        this.expenseQueryBuilder = expenseQueryBuilder;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.expenseSummaryExplainer = expenseSummaryExplainer;
        this.queryClassifier = queryClassifier;
        this.queryContextFormatter = queryContextFormatter;
    }

    /** Executes the query plan already produced by the unified interpreter with no additional model calls. */
    public SpeechResult handleInterpreted(String period, ConversationContext context) {
        return interpretedSummary(period, context);
    }

    private SpeechResult interpretedSummary(String period, ConversationContext context) {
        boolean whatsapp = "WHATSAPP".equalsIgnoreCase(context.getChannel());
        QueryResult result = new QueryResult();
        result.setIntent("QUERY");
        result.setQueryType("EXPENSE_TOTAL");
        result.setTimePeriod(period);
        result.setIncludeTotal(true);
        result.setGroupByCategory(true);
        ExpenseQuery query = expenseQueryBuilder.from(result, context.getUserId());
        ExpenseSummary summary = expenseAnalyticsService.analyze(query);
        com.apps.deen_sa.llm.AiCallTelemetry.avoided("query_classification_and_explanation");
        if (whatsapp) return portalInsight(highLevelSummary(context.getLocale(), period, summary));
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

}
