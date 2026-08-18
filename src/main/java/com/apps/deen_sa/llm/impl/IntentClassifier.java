package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.dto.IntentResult;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.apps.deen_sa.llm.PromptLoader;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

@Service
public class IntentClassifier extends BaseLLMExtractor {

    private final String systemPrompt;

    protected IntentClassifier(OpenAIClient client, ApplicationProperties properties, PromptLoader promptLoader) {
        super(client, properties);

        this.systemPrompt = promptLoader.combine(
                "llm/common/global_rules.md",
                "llm/intent/classify.md",
                "llm/intent/schema.json"
        );
    }

    public IntentResult classify(String text) {
        return callAndParse(
                systemPrompt,
                text,
                IntentResult.class
        );
    }
}
