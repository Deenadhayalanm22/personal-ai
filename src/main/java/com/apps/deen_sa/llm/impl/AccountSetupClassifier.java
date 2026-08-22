package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.config.ApplicationProperties;
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
            6. This extractor handles SETUP, RECORDING, and enrichment updates to an
               existing supported account, but not payments. For example, "My HDFC
               card bill is generated on the 1st" is a valid CREDIT_CARD update;
               extract the account name and details.billingDay.
            7. The currency defaults to "INR" when the user does not specify it.
            8. For any unsupported account type, return the INVALID CASE.
            9. currentValue is optional during initial setup. Never invent or
               default it to zero when omitted; return null. An explicitly stated
               zero is valid. Exact balance insights remain unavailable until a
               dated balance is supplied later.
            10. externalRefId is optional for BANK_ACCOUNT and CREDIT_CARD. Extract
                a safe user-provided identifier such as a nickname, masked account
                number, or last four digits. Never request or infer a full account
                or card number. If none is stated, return null.
            11. A supported account request with missing fields is STILL valid.
                Return valid = true, extract every value that is present, and set
                only the missing values to null. NEVER use the INVALID CASE merely
                because mandatory setup fields are missing; backend validation is
                responsible for asking follow-up questions.
            12. Use the institution/account wording as the name. For example,
                "Setup my HDFC bank account where I have 40k balance" means:
                name = "HDFC bank account", currentValue = 40000, and the safe
                account label may also be used as externalRefId.
            
            ----------------------------------------------------
            FIELD DEFINITIONS (VERY IMPORTANT)
            ----------------------------------------------------
            
            capacityLimit:
            - For credit cards, this is the credit limit.
            
            currentValue:
            - For CASH and BANK_ACCOUNT, this is the current balance.
            - For CREDIT_CARD, this is the current outstanding amount.
            - IF availableValue is not explicitly known → set availableValue = currentValue

            externalRefId:
            - For BANK_ACCOUNT and CREDIT_CARD, this identifies the specific account.
            - Use only a nickname, masked number, or last four digits explicitly
              provided by the user.
            - For CASH it is optional.
            - A clearly stated account label such as "HDFC bank account" or
              "HDFC salary account" is a valid safe identifier and may be used
              when no masked number or separate nickname is provided.

            ----------------------------------------------------
            USEFUL FIELDS BY ACCOUNT TYPE (ONLY NAME AND TYPE BLOCK CREATION)
            ----------------------------------------------------

            CASH:
            - name; currency and currentValue may be enriched later

            BANK_ACCOUNT:
            - name; currency, currentValue and externalRefId may be enriched later

            CREDIT_CARD:
            - name; current outstanding, externalRefId and capacityLimit may be enriched later
            - billingDay and dueDay are required so statement cycles can be tracked correctly

            details.billingDay:
            - For CREDIT_CARD, extract the monthly bill/statement generation day as an integer from 1 to 31.
            - Phrases such as "bill generated on the 1st", "billing date is 1", and
              "statement day 1" all mean {"billingDay": 1}.
            - This is the first day of the fresh statement cycle. Do not confuse it with the payment due day.
            - Always use the exact key "billingDay".

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
              "externalRefType": "BANK",
              "externalRefId": "Savings-1234",
              "details": {
                "billingDay": 1,
                "dueDay": 21
              },
              "rawText": ""
            }
            
            INVALID CASE:
            {
              "valid": false,
              "reason": "string"
            }

            Use INVALID CASE only when the requested account type is unsupported
            or the text is not an account-setup request. Missing fields do not make
            a supported setup request invalid.
            """;

    protected AccountSetupClassifier(OpenAIClient client, ApplicationProperties properties) {
        super(client, properties);
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

            The answer may be either a short answer or a complete restatement of
            the account setup. Extract the missing field AND every other account
            field explicitly supplied in this answer. Set fields not present in
            this answer to null so the application preserves previous data while
            merging. A supported setup remains valid even if other mandatory
            fields are still missing.
            Return JSON only.
            """.formatted(existing, missingField, userAnswer);

        return callAndParse(
                behaviorPrompt,
                followPrompt,
                AccountSetupDto.class
        );
    }

    public String generateFollowupQuestion(String field) {
        if ("currentValue".equals(field)) {
            return "What is the current balance or outstanding amount? You can enter 0 if there is none.";
        }
        if ("externalRefId".equals(field)) {
            return "What safe identifier should I use for this account, such as a nickname or the last four digits? Please do not share the full account or card number.";
        }
        if ("billingDay".equals(field)) {
            return "On which day of every month is the credit-card bill generated (the start of a fresh statement cycle)?";
        }
        if ("dueDay".equals(field)) {
            return "On which day of every month is the credit-card payment due?";
        }
        return "Please provide " + field.replaceAll("([A-Z])", " $1").toLowerCase();
    }
}
