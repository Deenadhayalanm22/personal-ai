package com.apps.deen_sa.llm;

import com.apps.deen_sa.config.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public abstract class BaseLLMExtractor {
    protected final OpenAIClient client;
    protected final ApplicationProperties properties;
    protected final ObjectMapper mapper;

    protected BaseLLMExtractor(OpenAIClient client, ApplicationProperties properties) {
        this.client = client;
        this.properties = properties;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    protected <T> T callAndParse(
            String systemPrompt,
            String userPrompt,
            Class<T> responseType
    ) {
        String json = callLLM(systemPrompt, userPrompt);

        try {
            return mapper.readValue(json, responseType);
        } catch (Exception e) {
            throw new RuntimeException(
                    "LLM returned invalid JSON for " + responseType.getSimpleName()
                            + ". Raw response: " + json,
                    e
            );
        }
    }

    protected String callLLM(String systemPrompt, String userPrompt) {
        String purpose = getClass().getSimpleName();
        long startedNanos = System.nanoTime();
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(properties.openai().model())
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .temperature(0.1)
                .build();

        ChatCompletion completion;
        try {
            completion = client.chat().completions().create(params);
            completion.usage().ifPresentOrElse(usage -> AiCallTelemetry.success(
                            purpose, completion.model(), usage.promptTokens(),
                            usage.promptTokensDetails().flatMap(details -> details.cachedTokens()).orElse(0L),
                            usage.completionTokens(), startedNanos),
                    () -> AiCallTelemetry.success(purpose, completion.model(), 0, 0, 0, startedNanos));
        } catch (RuntimeException failure) {
            AiCallTelemetry.failure(purpose, properties.openai().model(), startedNanos);
            throw failure;
        }

        return completion.choices().getFirst()
                .message()
                .content()
                .orElseThrow(() -> new RuntimeException("LLM returned no content"));
    }
}
