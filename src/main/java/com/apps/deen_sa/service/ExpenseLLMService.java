package com.apps.deen_sa.service;

import com.apps.deen_sa.dto.ExpenseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ExpenseLLMService {

    private final OpenAIClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${spring.security.allowed-origins}")
    private String openAiApiKey;

    public ExpenseLLMService() {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(openAiApiKey)
                .build();
    }

    private String buildSystemPrompt() {
        String today = OffsetDateTime.now().toLocalDate().toString();

        return """
             You are an expert finance extraction assistant.

            Your job: Convert the user's spending text into **valid JSON** matching the exact schema below.
            
            ### REQUIRED FIELDS:
            - amount (number)
            - category (string)
            
            ### OPTIONAL FIELDS:
            - subcategory (string)
            - merchant (string)
            - paymentMethod (string)   // UPI, Credit Card, Debit Card, Cash
            - spentAt (string, ISO format: YYYY-MM-DD) 
            - channel (string, e.g., Bike, Car, None)
            - notes (string)
            - details (object)
            
            ### RULES:
            1. Always extract amount and category.
            2. Categories: Fuel, Groceries, Food, Shopping, Transport, Bills, EMI, Medical, Others
            3. Subcategories examples:
               - Fuel → Petrol, Diesel
               - Groceries → Rice, Sugar, Vegetables, Snacks
               - Shopping → Amazon, Flipkart, Clothing, Electronics
            4. Merchant is any shop, app, or platform (e.g., Blinkit, Amazon, Indian Oil).
            5. spentAt:
               - Today’s date is %s
               - If date is mentioned → parse it.
               - If not mentioned → use today's date in YYYY-MM-DD.
            6. paymentMethod must be:
               - "UPI", "Credit Card", "Debit Card", or "Cash".
            7. Return ONLY JSON. No explanation, no text outside JSON.
            8. Do not include currency symbols.
            9. Use "details" to store any extra relevant fields (e.g., channel, tags, source app).
            10. If channel is not mentioned, return "None".
            
            ### OUTPUT JSON MUST MATCH THIS STRUCTURE:
            {
              "amount": number,
              "category": "string",
              "subcategory": "string",
              "merchant": "string",
              "paymentMethod": "string",
              "spentAt": "YYYY-MM-DD",
              "rawText": "string",
              "channel": "string",
              "notes": "string",
              "details": {}
            }
            
            Return only valid JSON.
            """.formatted(today);
    }

    public ExpenseDto extractExpense(String spokenText) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4_1)
                    .addSystemMessage(buildSystemPrompt())
                    .addUserMessage(spokenText)
                    .temperature(0.1)
                    .build();

            ChatCompletion response = client.chat().completions().create(params);
            String jsonResponse = response.choices().get(0)
                    .message()
                    .content()
                    .orElseThrow(() -> new RuntimeException("No content"));

            return mapper.readValue(jsonResponse, ExpenseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract expense: " + e.getMessage(), e);
        }
    }
}
