package com.apps.deen_sa.service;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Component;
import java.util.Map;

/** AI is deliberately limited to wording. Rules, figures, evidence and UI enums remain deterministic. */
@Component
public class AiMoneyStoryCopyGenerator extends BaseLLMExtractor implements MoneyStoryCopyGenerator {
    public AiMoneyStoryCopyGenerator(OpenAIClient client, ApplicationProperties properties) { super(client, properties); }

    @Override public Copy generate(MoneyStoryType type, Map<String, String> facts, Copy fallback) {
        if (properties.openai().apiKey() == null || properties.openai().apiKey().isBlank()) return fallback;
        try {
            Copy copy = callAndParse("""
                    Write concise personal-finance story copy. Return JSON only, with exactly these string fields:
                    heading, faceTheme, theme, eyebrow, title, body, comparisonTitle, comparisonBody.
                    heading is at most 20 characters. faceTheme must be one of warm, calm, focus, alert, action.
                    theme must be one of WARM_NOTICE, CALM_CONTEXT, FOCUS, HIGH_SPEND_ALERT, POSITIVE_ACTION.
                    You may use only facts supplied by the user prompt. Never invent or alter an amount,
                    percentage, count, date, merchant, category, comparison, or recommendation.
                    Keep the tone observational, non-judgmental, and no more than two short sentences per field.
                    """, "Story type: " + type + "\nFacts (use values exactly): " + facts, Copy.class);
            return valid(copy) ? copy : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    private boolean valid(Copy c) {
        return c != null && c.heading() != null && c.heading().length() <= 20
                && java.util.Set.of("warm","calm","focus","alert","action").contains(c.faceTheme())
                && java.util.Set.of("WARM_NOTICE","CALM_CONTEXT","FOCUS","HIGH_SPEND_ALERT","POSITIVE_ACTION").contains(c.theme())
                && c.eyebrow()!=null && c.title()!=null && c.body()!=null && c.comparisonTitle()!=null && c.comparisonBody()!=null;
    }
}
