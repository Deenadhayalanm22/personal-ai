package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.Registry.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.exception.LLMParsingException;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ExpenseClassifier extends BaseLLMExtractor {

    private final ExpenseTaxonomyRegistry expenseTaxonomyRegistry;
    private final String behaviorPrompt;

    protected ExpenseClassifier(OpenAIClient client,
                                ExpenseTaxonomyRegistry expenseTaxonomyRegistry) {
        super(client);
        this.expenseTaxonomyRegistry = expenseTaxonomyRegistry;
        this.behaviorPrompt = buildBehaviorPrompt();
    }

    // ---------------------------------------------
    // SYSTEM BEHAVIOR PROMPT (reused for all calls)
    // ---------------------------------------------
    private String buildBehaviorPrompt() {
        String today = OffsetDateTime.now().toLocalDate().toString();

        return """
        You are an expert finance extraction assistant.
        Your behavior is ALWAYS governed by the following rules.

        TODAY'S DATE = %s and Default currency is Rs from India

        ---------------------------------------------------
        ### 3–TIER CLASSIFICATION MODEL
                  
          ### TIER 1 & TIER 2 → CATEGORY(broad intent) & SUBCATEGORY (refined type)
          Use one of these if clear, else return null:
          %s
          
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
                  
            1. amount
               - Extract numeric amount if explicitly mentioned.
               - If unclear → null.
        
            2. merchantName
               - Extract shop, platform, or service provider.
               - Examples: Amazon, Swiggy, Indian Oil.
               - If not mentioned → null.
        
            3. transactionDate
               - If explicit date exists → convert to YYYY-MM-DD.
               - If relative date ("yesterday", "last week") → compute.
               - If absent → use TODAY'S DATE.
        
            4. rawText
               - Copy the user's original message EXACTLY.
        
            5. tags
               - Add meaningful semantic tags.
               - Tags are for high-level personal finance analytics.
               - Do NOT repeat category or subcategory.
               - Do NOT tag item names
               - lowercase, single-word nouns/adjectives only. No expense/spend/payment words. WHAT/WHO only.
        
            6. details
               - Include extra structured information ONLY if explicitly mentioned.
               - Examples:
                 vehicleType, platform, invoiceNumber, cardLast4, litres, peopleCount
               - If none → return {}.
               
            7. sourceAccount
               - Extract the account or wallet from which the payment was made.
               - Can be - "CREDIT_CARD" or "BANK_ACCOUNT" or "WALLET" or "CASH"
               - UPI always implies sourceAccount = BANK_ACCOUNT
               - If unclear → null.
        
            ---------------------------------------------------
            ### OUTPUT JSON SCHEMA (STRICT)
        
            {
              "valid": true,
              "amount": number | null,
              "category": string | null,
              "subcategory": string | null,
              "merchantName": string | null,
              "sourceAccount": string | null,
              "transactionDate": "YYYY-MM-DD" | null,
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
            ### NON-NEGOTIABLE RULES
        
            - Never guess or hallucinate.
            - Never create new categories or subcategories.
            - If a field is unclear → set it to null.
            - Do NOT include fields not defined in the schema.
            - Do NOT add explanations or text outside JSON.
            - This extractor handles EXPENSES ONLY.
            - tags MUST be returned as a top-level array field.
            - tags MUST NOT be placed inside details.
            - details is reserved only for structured fields defined by subcategory contracts.
        
            ---------------------------------------------------
            """.formatted(today, renderTaxonomy(expenseTaxonomyRegistry));
    }

    private String renderTaxonomy(ExpenseTaxonomyRegistry registry) {
        StringBuilder sb = new StringBuilder();
        registry.categories().forEach(cat -> {
            sb.append("- ").append(cat).append(":\n");
            registry.subcategoriesFor(cat)
                    .forEach(sub -> sb.append("  - ").append(sub).append("\n"));
        });
        return sb.toString();
    }

    // ---------------------------------------------
    // MAIN EXPENSE EXTRACTION
    // ---------------------------------------------
    public ExpenseDto extractExpense(String spokenText) {
        String userPrompt = "Extract the financial transaction from: \"" + spokenText + "\"";
        return callAndParse(
                behaviorPrompt,
                userPrompt,
                ExpenseDto.class
        );
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

            return callAndParse(
                    behaviorPrompt,
                    followPrompt,
                    ExpenseDto.class
            );
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
