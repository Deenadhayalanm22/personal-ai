package com.apps.deen_sa.handler;

import com.apps.deen_sa.dto.ExpenseQuery;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.dto.QueryResult;
import com.apps.deen_sa.formatter.QueryContextFormatter;
import com.apps.deen_sa.llm.impl.ExpenseSummaryExplainer;
import com.apps.deen_sa.llm.impl.QueryClassifier;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechHandler;
import com.apps.deen_sa.orchestrator.SpeechResult;
import com.apps.deen_sa.resolver.ExpenseQueryBuilder;
import com.apps.deen_sa.service.ExpenseAnalyticsService;
import org.springframework.stereotype.Service;

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
            ExpenseSummaryExplainer expenseSummaryExplainer, QueryClassifier queryClassifier, QueryContextFormatter queryContextFormatter
    ) {
        this.expenseQueryBuilder = expenseQueryBuilder;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.expenseSummaryExplainer = expenseSummaryExplainer;
        this.queryClassifier = queryClassifier;
        this.queryContextFormatter = queryContextFormatter;
    }

    @Override
    public SpeechResult handleSpeech(String userText, ConversationContext ctx) {
        QueryResult result = queryClassifier.classify(userText);

        ExpenseQuery query = expenseQueryBuilder.from(result);
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
