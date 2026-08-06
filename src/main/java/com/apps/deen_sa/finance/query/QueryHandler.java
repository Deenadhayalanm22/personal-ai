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
import com.apps.deen_sa.conversation.ConversationMessages;

@Service
public class QueryHandler implements SpeechHandler {

    private final ExpenseQueryBuilder expenseQueryBuilder;
    private final ExpenseAnalyticsService expenseAnalyticsService;
    private final ExpenseSummaryExplainer expenseSummaryExplainer;
    private final QueryClassifier queryClassifier;
    private final QueryContextFormatter queryContextFormatter;
    private final ConversationMessages messages;

    public QueryHandler(
            ExpenseQueryBuilder expenseQueryBuilder,
            ExpenseAnalyticsService expenseAnalyticsService,
            ExpenseSummaryExplainer expenseSummaryExplainer, QueryClassifier queryClassifier,
            QueryContextFormatter queryContextFormatter, ConversationMessages messages
    ) {
        this.expenseQueryBuilder = expenseQueryBuilder;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.expenseSummaryExplainer = expenseSummaryExplainer;
        this.queryClassifier = queryClassifier;
        this.queryContextFormatter = queryContextFormatter;
        this.messages = messages;
    }

    /** Executes the query plan already produced by the unified interpreter with no additional model calls. */
    public SpeechResult handleInterpreted(String period, ConversationContext context) {
        QueryResult result = new QueryResult();
        result.setIntent("QUERY");
        result.setQueryType("EXPENSE_TOTAL");
        result.setTimePeriod(period);
        result.setIncludeTotal(true);
        result.setGroupByCategory(true);
        ExpenseQuery query = expenseQueryBuilder.from(result, context.getUserId());
        ExpenseSummary summary = expenseAnalyticsService.analyze(query);
        com.apps.deen_sa.llm.AiCallTelemetry.avoided("query_classification_and_explanation");
        return SpeechResult.info(messages.summary(context.getLocale(), period, summary));
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
}
