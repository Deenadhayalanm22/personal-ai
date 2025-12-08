package com.apps.deen_sa.llm;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.exception.LLMParsingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ExpenseClassifier {

    private final OpenAIClient client;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final String behaviorPrompt = buildBehaviorPrompt();


    public ExpenseClassifier(@Value("${openai.api-key}") String apiKey) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    // ---------------------------------------------
    // SYSTEM BEHAVIOR PROMPT (reused for all calls)
    // ---------------------------------------------
    private String buildBehaviorPrompt() {
        String today = OffsetDateTime.now().toLocalDate().toString();

        return """
        You are an expert finance extraction assistant.
        Your behavior is ALWAYS governed by the following rules.

        TODAY'S DATE = %s

        ---------------------------------------------------
        ### 3–TIER CLASSIFICATION MODEL
                  
          ### TIER 1 → CATEGORY (broad intent)
          Use one of these if clear, else return null:
          - "Food & Dining"
          - "Transportation"
          - "Shopping"
          - "Housing"
          - "Utilities"
          - "Medical"
          - "Entertainment"
          - "Education"
          - "Family Support"
          - "Miscellaneous"
          
          ### TIER 2 → SUBCATEGORY (refined type)
          Examples:
          
          **Food & Dining**
          - "Groceries"
          - "Eating Out"
          - "Snacks & Beverages"
          - "Celebration Meal/Home Cooked"
          
          **Transportation**
          - "Fuel"
          - "Public Transport"
          - "Parking"
          - "Toll"
          
          **Shopping**
          - "Electronics"
          - "Clothing"
          - "Online Purchase"
          - "Gifts"
          
          If unclear → null.
          
          ### TIER 3 → CONTEXT TAGS (deep meaning)
          Extract tags that reveal additional semantic meaning.
          Examples:
          - "restaurant"
          - "home_cooked"
          - "delivery"
          - "takeaway"
          - "coffee"
          - "snacks"
          - "monthly_ration"
          - "commute"
          - "long_drive"
          - "car"
          - "bike"
          - "grocery"
          - "celebration"
          - "family_dinner"
          - "travel_related"
          - "healthcare"
          - "bill_payment"
          If unclear → empty array [].
          
          ### EXTRACTION RULES
                  
          1. Extract all fields you can **confidently** identify.
          2. If a field is missing or unclear → set it to null. Do NOT guess.
          3. Subcategory must be consistent with the Category.
          4. Tags must reflect **deep meaning**, not just repetition of category/subcategory.
          5. merchantName:
             - Extract shop, app, or provider name (e.g., Amazon, Blinkit, Indian Oil).
          6. paymentMethod:
             Must be EXACTLY one of:
             - "UPI", "Credit Card", "Debit Card", "Cash"
             If unclear → null.
          7. spentAt:
             - If explicit date: convert to YYYY-MM-DD.
             - If relative date: compute (e.g., "yesterday", "last week").
             - If absent → use today's date.
          8. rawText:
             - Copy the original message exactly as spoken.
          9. details:
             - Store extra structured info (vehicle type, platform, invoice number, etc.)
             - If none → return {}.
          10. tags:
             - Add meaningful context tags.
             - If none → return [].

        ---------------------------------------------------
        ### OUTPUT SCHEMA (ALWAYS USE THIS)
        {
          "valid": true,
          "amount": number | null,
          "category": string | null,
          "subcategory": string | null,
          "merchantName": string | null,
          "paymentMethod": string | null,
          "spentAt": "YYYY-MM-DD" | null,
          "rawText": string,
          "details": {},
          "tags": []
        }

        INVALID CASE:
        {
          "valid": false,
          "reason": "string"
        }

        ---------------------------------------------------
        ### RULES YOU MUST ALWAYS FOLLOW
        - Never guess or hallucinate.
        - If a field is unclear → set it to null.
        - Never create your own categories.
        - Never output anything outside JSON.
        - Always follow 3-tier model.
        - Missing data → null (backend will ask follow-up).
        ---------------------------------------------------
        """.formatted(today);
    }

    // ---------------------------------------------
    // UNIVERSAL LLM CALL WRAPPER
    // ---------------------------------------------
    private String callLLM(String systemPrompt, String userPrompt) {

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

    // ---------------------------------------------
    // MAIN EXPENSE EXTRACTION
    // ---------------------------------------------
    public ExpenseDto extractExpense(String spokenText) {
        try {
            String userPrompt = "Extract the financial transaction from: \"" + spokenText + "\"";

            String json = callLLM(behaviorPrompt, userPrompt);

            return mapper.readValue(json, ExpenseDto.class);
        } catch (JsonProcessingException e) {
            throw new LLMParsingException("Failed to parse LLM JSON response", e);
        }
    }

    // ---------------------------------------------
    // FOLLOW-UP FIELD REFINEMENT
    // ---------------------------------------------
    public ExpenseDto extractFieldFromFollowup(ExpenseDto existing,
                                               String missingField,
                                               String userAnswer) {
        try {
            String followPrompt = """
                Refine the missing field for the user's transaction.
        
                PREVIOUS PARTIAL DATA:
                %s
        
                MISSING FIELD:
                %s
        
                USER ANSWER:
                "%s"
        
                RULE:
                - Extract only this field and its dependent fields (e.g., category→subcategory→tags).
                - Never invent values.
                - Return ONLY updated fields in JSON.
                """.formatted(
                    mapper.writeValueAsString(existing),
                    missingField,
                    userAnswer
            );

            String json = callLLM(behaviorPrompt, followPrompt);

            return mapper.readValue(json, ExpenseDto.class);
        } catch (JsonProcessingException e) {
            throw new LLMParsingException("Failed to parse LLM JSON response", e);
        }
    }

    // ---------------------------------------------
    // FOLLOW-UP QUESTION GENERATION
    // ---------------------------------------------
    public String generateFollowupQuestionForExpense(String missingField, ExpenseDto dto) {

        String prompt = """
            CURRENT EXTRACTED DATA:
            %s

            The backend still needs this field: %s

            Generate a short natural follow-up question.
            Do NOT return JSON.
            """.formatted(dto, missingField);

        return callLLM(behaviorPrompt, prompt);
    }
}
