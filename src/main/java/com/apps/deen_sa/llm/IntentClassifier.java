package com.apps.deen_sa.llm;

import com.apps.deen_sa.dto.IntentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IntentClassifier {
    private final OpenAIClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String INTENT_PROMPT = """
        You are a financial intent classifier.
        
        Classify the user's statement into EXACTLY one of these intents:
        
        - EXPENSE
        - INCOME
        - INVESTMENT
        - LOAN_PAYMENT
        - TRANSFER
        - UNKNOWN
        
        Return JSON only:
        
        {
          "intent": "...",
          "confidence": 0.0 to 1.0
        }
        
        IMPORTANT:
        - ALWAYS output raw JSON only.
        - No markdown.
        - No backticks.
        - No explanations.
        - No surrounding text.
        
        User message:
        "%s"
        """;

    public IntentClassifier(@Value("${openai.api-key}") String apiKey) {
        this.client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    public IntentResult classify(String text) {
        String prompt = INTENT_PROMPT.formatted(text);

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_MINI)
                .addUserMessage(prompt)
                .build();

        ChatCompletion result = client.chat().completions().create(params);

        String json = result.choices().getFirst().message().content().orElseThrow();

        try {
            return mapper.readValue(json, IntentResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid intent JSON: " + json);
        }
    }
}
