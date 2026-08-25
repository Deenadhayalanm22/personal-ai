package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.dto.TagMatchResult;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Matches expense wording to the fixed category taxonomy; it does not create transaction tags. */
@Component
public class TaxonomySemanticMatcher extends BaseLLMExtractor {
    private static final String PROMPT = """
        You are a taxonomy semantic matcher.

        Match RAW VALUES to EXISTING CANONICAL TAXONOMY VALUES.

        Rules:
        - Only match when the meaning is clearly the same.
        - Do not invent or modify canonical values.
        - If no good match exists, return null.

        Existing canonical values:
        %s

        Raw values:
        %s

        Return STRICT JSON:
        {
          "matches": {
            "rawValue": "canonicalValue | null"
          }
        }
        """;

    public TaxonomySemanticMatcher(OpenAIClient client, ApplicationProperties properties) {
        super(client, properties);
    }

    public Map<String, String> match(List<String> canonicalValues, List<String> rawValues) {
        return callAndParse(PROMPT.formatted(canonicalValues, rawValues), "", TagMatchResult.class).matches();
    }
}
