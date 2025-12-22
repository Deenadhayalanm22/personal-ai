package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.dto.IntentResult;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

@Service
public class IntentClassifier extends BaseLLMExtractor {

    private static final String INTENT_PROMPT = """
        You are a financial intent classifier.

        Your task is to classify the user's message into EXACTLY ONE of the following intents:
        
        - EXPENSE
        - QUERY
        - INCOME
        - INVESTMENT
        - TRANSFER
        - ACCOUNT_SETUP
        - UNKNOWN
        
        Return STRICT JSON only:
        {
          "intent": "...",
          "confidence": 0.0 to 1.0
        }
        
        ----------------------------------------
        IMPORTANT DEFINITIONS (READ CAREFULLY)
        ----------------------------------------
        
        ACCOUNT_SETUP:
        Use this intent when the user is DECLARING, RECORDING, or DESCRIBING
        an existing or new financial account, obligation, or value container.
        
        This includes:
        - Bank accounts
        - Credit cards
        - Wallets
        - Loans (personal loan, phone loan, consumer loan, instant loan, etc.)
        - Inventory or stock
        - Salary accounts
        - Outstanding balances
        - EMI details, tenure, due dates (as long as no payment is happening)
        
        Examples (ALL are ACCOUNT_SETUP):
        - "I have a personal loan of 5 lakhs"
        - "Outstanding amount is 3.2 lakhs"
        - "EMI is 15k and ends in Dec 2027"
        - "I have 2 running loans from HDFC"
        - "Add a credit card with 50k limit"
        - "Create a salary account with 10k balance"
        
        IMPORTANT:
        Mentioning EMI, outstanding amount, tenure, or loan details
        DOES NOT mean a payment is happening.
        
        ----------------------------------------
        
        EXPENSE:
        Use this intent ONLY when the user is SPENDING money or MAKING A PAYMENT.
        
        This includes:
        - Purchases
        - Bills
        - EMI payments
        - Fees
        - Subscriptions
        
        A message is an EXPENSE only if it contains an EXPLICIT PAYMENT ACTION.
        
        Payment verbs include (not exhaustive):
        - paid
        - pay
        - paying
        - spent
        - debited
        - deducted
        - transferred
        - sent
        - charged
        
        Examples (ALL are EXPENSE):
        - "Paid my loan EMI today"
        - "EMI of 15k got deducted"
        - "Spent 500 on groceries"
        - "Paid credit card bill"
        
        ----------------------------------------
        
        INCOME:
        Money coming IN.
        
        Examples:
        - "Got my salary"
        - "Received 20k from client"
        
        ----------------------------------------
        
        INVESTMENT:
        Money being invested or allocated for returns.
        
        Examples:
        - "Invested 10k in mutual fund"
        - "Bought shares of TCS"
        
        ----------------------------------------
        
        TRANSFER:
        Moving money between two owned accounts.
        
        Examples:
        - "Transferred 5k from bank to wallet"
        - "Moved money from savings to credit card"
        
        ----------------------------------------
        
        CRITICAL RULES (NON-NEGOTIABLE)
        
        1. If the user is DESCRIBING EXISTENCE or STATE → ACCOUNT_SETUP
        2. If the user is PERFORMING AN ACTION → EXPENSE / INCOME / TRANSFER
        3. EMI mentioned WITHOUT a payment verb → ACCOUNT_SETUP
        4. EMI mentioned WITH a payment verb → EXPENSE
        5. If intent is unclear → UNKNOWN
        6. NEVER guess user intent
        7. NEVER output anything except raw JSON
        
        ----------------------------------------
        
        User message:
        "%s"
        """;

    protected IntentClassifier(OpenAIClient client) {
        super(client);
    }

    public IntentResult classify(String text) {
        return callAndParse(
                INTENT_PROMPT.formatted(text),
                text,
                IntentResult.class
        );
    }
}
