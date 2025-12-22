package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LoanQueryExplainer extends BaseLLMExtractor {

    private static final String PROMPT = """
        You are a financial assistant.
        Explain the loan EMI status clearly using the provided facts.
        Do NOT invent numbers.
        Do NOT perform calculations.

        Loan summary:
        %s
        """;

    public LoanQueryExplainer(OpenAIClient client) {
        super(client);
    }

    public String explainEmiRemaining(Map<String, Object> summary) {
        return callLLM(
                "Explain loan EMI status",
                PROMPT.formatted(summary)
        );
    }
}
