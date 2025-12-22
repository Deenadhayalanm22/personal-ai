package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.dto.TagMatchResult;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TagSemanticMatcher extends BaseLLMExtractor {

    private static final String PROMPT = """
        You are a semantic matcher.

        Your job is to match RAW TAGS to EXISTING CANONICAL TAGS.

        Rules:
        - Only match if meaning is clearly the same.
        - Do NOT invent new tags.
        - Do NOT modify canonical tags.
        - If no good match exists, return null.

        Existing canonical tags:
        %s

        Raw tags:
        %s

        Return STRICT JSON:
        {
          "matches": {
            "rawTag": "canonicalTag | null"
          }
        }
        """;

    public TagSemanticMatcher(OpenAIClient client) {
        super(client);
    }

    public Map<String, String> match(
            List<String> existingCanonicalTags,
            List<String> rawTags
    ) {
        return callAndParse(
                PROMPT.formatted(existingCanonicalTags, rawTags),
                "",
                TagMatchResult.class
        ).matches();
    }
}
