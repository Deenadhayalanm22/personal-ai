package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.dto.LiabilityPaymentDto;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.apps.deen_sa.llm.PromptLoader;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

@Service
public class LiabilityPaymentClassifier extends BaseLLMExtractor {

    private final String systemPrompt;

    protected LiabilityPaymentClassifier(OpenAIClient client, ApplicationProperties properties,
                                         PromptLoader promptLoader) {
        super(client, properties);

        this.systemPrompt = promptLoader.combine(
                "llm/common/global_rules.md",
                "llm/payment/extract.md",
                "llm/payment/schema.json"
        );
    }

    public LiabilityPaymentDto extractPayment(String text) {
        return callAndParse(
                systemPrompt,
                text,
                LiabilityPaymentDto.class
        );
    }
}
