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
                    """ + styleInstruction(type), "Story type: " + type + "\nFacts (use values exactly): " + facts, Copy.class);
            return valid(copy) ? copy : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    @Override public CommitmentRunwayCopy generateCommitmentRunway(Map<String, String> facts, CommitmentRunwayCopy fallback) {
        if (properties.openai().apiKey() == null || properties.openai().apiKey().isBlank()) return fallback;
        try {
            CommitmentRunwayCopy copy = callAndParse("""
                    Write linked personal-finance copy for a two-card Monthly Commitment story. Return JSON only:
                    {"current": {heading, faceTheme, theme, eyebrow, title, body, comparisonTitle, comparisonBody},
                    "next": {heading, faceTheme, theme, eyebrow, title, body, comparisonTitle, comparisonBody}}.
                    Each nested object uses the same exact string fields and enum restrictions as a normal story card.
                    Use only supplied facts exactly; never invent or calculate money, dates, payment counts, or commitments.
                    The current card makes one playful, safe payoff hook. The next card is a short continuation of that
                    same thought, looking ahead to nextMonth. Together they should read like one tiny scene, not two reports.
                    Use at most one emoji across both cards. Each eyebrow has at most 5 words, title at most 7 words,
                    and body at most 18 words. Do not repeat totals or bucket figures in body/title because the UI shows them.
                    Never joke about debt stress, missed payments, low balances, or the user.
                    """, "Story type: MONTHLY_COMMITMENT\nVerified facts (use values exactly): " + facts, CommitmentRunwayCopy.class);
            return valid(copy == null ? null : copy.current()) && valid(copy == null ? null : copy.next()) ? copy : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    private boolean valid(Copy c) {
        return c != null && c.heading() != null && c.heading().length() <= 20
                && java.util.Set.of("warm","calm","focus","alert","action").contains(c.faceTheme())
                && java.util.Set.of("WARM_NOTICE","CALM_CONTEXT","FOCUS","HIGH_SPEND_ALERT","POSITIVE_ACTION").contains(c.theme())
                && c.eyebrow()!=null && c.title()!=null && c.body()!=null && c.comparisonTitle()!=null && c.comparisonBody()!=null;
    }

    private String styleInstruction(MoneyStoryType type) {
        if (type != MoneyStoryType.MONTHLY_COMMITMENT) return "";
        return """

                This is a Monthly Commitment story for a trusted closed-circle finance app. Use a warm, playful,
                celebratory voice—not a formal advisor voice. You may use at most one fitting emoji across all fields.
                Make one punchline, not a financial report. heading has at most 16 characters, eyebrow at most 5 words,
                title at most 7 words, and body at most 18 words. Mention only the single soonestPayoff; never list a
                second loan or say \"plus\". Do not repeat the total or bucket figures in title/body because the UI shows them.
                When loanPayoffFacts say a payment ends soon, make that the hook using light phrases such as
                \"leaving the chat\", \"escape artist\", \"final episode\", \"money squad\", or \"wallet comeback arc\".
                State the supplied end month, remaining-payment count, amount freed, and free-from month exactly when used.
                Never joke about debt, missed payments, low balances, or the user; the playfulness is about a commitment
                ending and money becoming available. If no payoff fact is supplied, use the monthly cast/squad theme and
                do not imply that any payment is ending.
                """;
    }
}
