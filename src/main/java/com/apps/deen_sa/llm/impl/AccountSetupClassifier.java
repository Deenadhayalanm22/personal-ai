package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

@Service
public class AccountSetupClassifier extends BaseLLMExtractor {

    private final String behaviorPrompt = """
            You are a basic account setup extraction assistant.

            Your task is to extract structured data for creating or recording
            one of these supported account types:

            - CASH
            - BANK_ACCOUNT
            - CREDIT_CARD

            You MUST follow the rules below strictly.
            
            ----------------------------------------------------
            GENERAL RULES (APPLY TO ALL)
            ----------------------------------------------------
            
            1. Do NOT guess values.
            2. Do NOT calculate values.
            3. Extract ONLY what the user explicitly states.
            4. If a value is unclear or missing, set it to null.
            5. Always return STRICT JSON only.
            6. This extractor handles SETUP / RECORDING only, not payments.
            7. The currency defaults to "INR" when the user does not specify it.
            8. For any unsupported account type, return the INVALID CASE.
            
            ----------------------------------------------------
            FIELD DEFINITIONS (VERY IMPORTANT)
            ----------------------------------------------------
            
            capacityLimit:
            - For credit cards, this is the credit limit.
            
            currentValue:
            - For CASH and BANK_ACCOUNT, this is the current balance.
            - For CREDIT_CARD, this is the current outstanding amount.
            - IF availableValue is not explicitly known → set availableValue = currentValue

            details.dueDay:
            - For CREDIT_CARD, extract the monthly payment due day as an integer from 1 to 31.
            - Treat cardinal and ordinal forms identically (for example, "21", "21st",
              and "day 21 of every month" all mean {"dueDay": 21}).
            - Always use the exact key "dueDay". Never return "dueDate" for this value.
            
            ----------------------------------------------------
            OUTPUT JSON SCHEMA (STRICT)
            ----------------------------------------------------
            
            {
              "valid": true,
              "ownerType": "USER",
              "ownerId": null,
              "containerType": "BANK_ACCOUNT",
              "name": "Salary Account",
              "currency": "INR",
              "capacityLimit": null,
              "currentValue": 21000,
              "availableValue": 21000,
              "minThreshold": null,
              "externalRefType": null,
              "externalRefId": null,
              "details": {
                "dueDay": 21
              },
              "rawText": ""
            }
            
            INVALID CASE:
            {
              "valid": false,
              "reason": "string"
            }
            """;

    protected AccountSetupClassifier(OpenAIClient client) {
        super(client);
    }

    public AccountSetupDto extractAccount(String text) {
        return callAndParse(
                behaviorPrompt,
                "Extract account setup details from: \"" + text + "\"",
                AccountSetupDto.class
        );
    }

    public AccountSetupDto extractFieldFromFollowup(
            AccountSetupDto existing,
            String missingField,
            String userAnswer
    ) {
        String followPrompt = """
            PREVIOUS DATA:
            %s

            MISSING FIELD:
            %s

            USER ANSWER:
            "%s"

            Extract ONLY the missing field.
            Return JSON only.
            """.formatted(existing, missingField, userAnswer);

        return callAndParse(
                behaviorPrompt,
                followPrompt,
                AccountSetupDto.class
        );
    }

    public String generateFollowupQuestion(String field) {
        return "Please provide " + field.replaceAll("([A-Z])", " $1").toLowerCase();
    }
}
