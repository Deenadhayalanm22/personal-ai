package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Component;

@Component
public class ExpenseSummaryExplainer extends BaseLLMExtractor {

    private static final String PROMPT = """
            You are a personal finance assistant.
            
              The following expense data is already FILTERED.
              Do NOT assume it represents overall spending.
            
              Filter context:
              %s
            
              Expense Summary:
              %s
            
            Explain the result clearly in one short paragraph.
           """;

    public ExpenseSummaryExplainer(OpenAIClient client) {
        super(client);
    }

    public String explain(ExpenseSummary summary, String question, String context) {
        return callLLM( PROMPT.formatted(context, summary.toString()), question);
    }
}
