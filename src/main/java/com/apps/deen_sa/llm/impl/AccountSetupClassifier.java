package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

@Service
public class AccountSetupClassifier extends BaseLLMExtractor {

    private final String behaviorPrompt = """
            You are a VALUE CONTAINER SETUP extraction assistant.
            
            Your task is to extract structured data for creating or recording
            a VALUE CONTAINER such as accounts, loans, cards, wallets, inventory.
            
            You MUST follow the rules below strictly.
            
            ----------------------------------------------------
            SUPPORTED containerType VALUES
            ----------------------------------------------------
            
            - CASH
            - BANK_ACCOUNT
            - CREDIT_CARD
            - WALLET
            - PAYABLE        (Loans, EMIs, obligations)
            - RECEIVABLE
            - INVENTORY
            
            ----------------------------------------------------
            GENERAL RULES (APPLY TO ALL)
            ----------------------------------------------------
            
            1. Do NOT guess values.
            2. Do NOT calculate values.
            3. Extract ONLY what the user explicitly states.
            4. If a value is unclear or missing, set it to null.
            5. Always return STRICT JSON only.
            6. This extractor handles SETUP / RECORDING only, not payments.
            
            ----------------------------------------------------
            FIELD DEFINITIONS (VERY IMPORTANT)
            ----------------------------------------------------
            
            capacityLimit:
            - Represents the TOTAL VALUE of the container.
            - For loans (PAYABLE), this is the TOTAL LOAN AMOUNT.
            - For credit cards, this is the CREDIT LIMIT.
            
            currentValue:
            - Represents the CURRENT OUTSTANDING VALUE.
            - For new loans, if not explicitly mentioned, set to null.
              (Backend may default it later.)
            - IF availableValue is not explicitly known → set availableValue = currentValue
            
            ----------------------------------------------------
            PAYABLE (LOAN) SPECIFIC RULES
            ----------------------------------------------------
            
            When containerType = PAYABLE, extract the following:
            
            MANDATORY FIELDS (if mentioned):
            - capacityLimit → total loan amount
            - details.emiAmount → monthly EMI amount
            - details.tenureMonths → total number of EMIs
            - details.dueDay → day of month EMI is due
            - details.startDate / endDate → if explicitly stated
            
            MAPPING RULES (CRITICAL):
            - Phrases like:
              - "took a phone for 21k in EMI"
              - "loan of 5 lakhs"
              - "bought for 30k on EMI"
              MUST map the amount to capacityLimit.
            - "6 EMI", "6 months EMI" → details.tenureMonths = 6
            - "monthly 3.5k", "EMI is 3,500" → details.emiAmount
            - "due on 21st", "paid every month on 21st" → details.dueDay = 21
            
            DO NOT OMIT these fields if they are explicitly present.
            
            ----------------------------------------------------
            OUTPUT JSON SCHEMA (STRICT)
            ----------------------------------------------------
            
            {
              "valid": true,
              "ownerType": "USER",
              "ownerId": null,
              "containerType": "PAYABLE",
              "name": "Mobile Loan",
              "currency": "INR",
              "capacityLimit": 21000,
              "currentValue": null,
              "availableValue": null,
              "minThreshold": null,
              "externalRefType": null,
              "externalRefId": null,
              "details": {
                "emiAmount": 3500,
                "tenureMonths": 6,
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
