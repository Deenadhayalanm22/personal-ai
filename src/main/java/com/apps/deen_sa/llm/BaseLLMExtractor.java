package com.apps.deen_sa.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public abstract class BaseLLMExtractor {
    protected final OpenAIClient client;
    protected final ObjectMapper mapper;

    protected BaseLLMExtractor(OpenAIClient client) {
        this.client = client;
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
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_MINI)
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .temperature(0.1)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);

        return completion.choices().getFirst()
                .message()
                .content()
                .orElseThrow(() -> new RuntimeException("LLM returned no content"));
    }
}
