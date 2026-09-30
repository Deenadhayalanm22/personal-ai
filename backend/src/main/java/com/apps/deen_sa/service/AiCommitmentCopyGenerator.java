package com.apps.deen_sa.service;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Component;
import java.util.Map;

/** AI is deliberately limited to wording. Rules, figures, evidence and UI enums remain deterministic. */
@Component
public class AiCommitmentCopyGenerator extends BaseLLMExtractor implements CommitmentCopyGenerator {
    public AiCommitmentCopyGenerator(OpenAIClient client, ApplicationProperties properties) { super(client, properties); }

    @Override public CommitmentRunwayCopy generateCommitmentRunway(Map<String, String> facts, CommitmentRunwayCopy fallback) {
        if (properties.openai().apiKey() == null || properties.openai().apiKey().isBlank()) return fallback;
        try {
            CommitmentRunwayCopy copy = callAndParse("""
                    Write linked personal-finance copy for two Monthly Commitment cards. Return JSON only:
                    {"current": {heading, faceTheme, theme, eyebrow, title, body, comparisonTitle, comparisonBody},
                    "next": {heading, faceTheme, theme, eyebrow, title, body, comparisonTitle, comparisonBody}}.
                    Each nested object uses the same exact string fields and enum restrictions as a commitment card.
                    Use only supplied facts exactly; never invent or calculate money, dates, payment counts, or commitments.
                    The current card makes one playful, safe payoff hook. The next card is a short continuation of that
                    same thought, looking ahead to nextMonth. Together they should read like one tiny scene, not two reports.
                    Use at most one emoji across both cards. Each eyebrow has at most 5 words, title at most 7 words,
                    and body at most 18 words. Do not repeat totals or bucket figures in body/title because the UI shows them.
                    Never joke about debt stress, missed payments, low balances, or the user.
                    """, "Presentation: MONTHLY_COMMITMENT\nVerified facts (use values exactly): " + facts, CommitmentRunwayCopy.class);
            return valid(copy == null ? null : copy.current()) && valid(copy == null ? null : copy.next()) ? copy : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    private boolean valid(Copy c) {
        return c != null && c.heading() != null && c.heading().length() <= 20
                && java.util.Set.of("warm","calm","focus","alert","action").contains(c.faceTheme())
                && java.util.Set.of("WARM_NOTICE","CALM_CONTEXT","FOCUS","HIGH_SPEND_ALERT","POSITIVE_ACTION").contains(c.theme())
                && c.eyebrow()!=null && c.title()!=null && c.body()!=null && c.comparisonTitle()!=null && c.comparisonBody()!=null;
    }

}
