package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;

final class FinanceInterpretationPrompt implements InterpretationPromptContributor {
    @Override public String instructions() { return """
            PERSONAL-FINANCE EXTENSION
            Supported event types: EXPENSE, INCOME, TRANSFER, LIABILITY_PAYMENT, ACCOUNT_SETUP, ASSET_BUY, ASSET_SELL, BUDGET_SET.
            Fields: amount, category, subcategory, merchantName, sourceAccount, destinationAccount, sourceBalance,
            creditLimit, creditCardDueDay (1-31), transactionDate (YYYY-MM-DD), tags, rawText.
            - A new financial movement amount must have exact evidence in the current message.
            - Existing accounts are reference candidates only. Populate an account only when stated now or when it
              directly answers a pending question.
            - UPI, bank transfer, debit card, FASTag, and bank auto-debit map to BANK_ACCOUNT; cash maps to CASH;
              credit card/card EMI maps to CREDIT_CARD; wallet maps to WALLET.
            - ACCOUNT_SETUP attributes such as balance, outstanding, limit and due day are not transaction amounts.
              Preserve the complete current message in rawText for deterministic extraction by the capability.
            - Incoming salary, receipts, refunds, interest and gifts are INCOME; a receiving account is destinationAccount.
            - Paying a credit-card bill or loan is LIABILITY_PAYMENT. Buying goods using a card remains EXPENSE.
            - Use a broad category and a specific subcategory. Never invent a date, account, amount or classification.
            - "Set my monthly X budget to Y" is BUDGET_SET: amount=Y and category=X.
            - Expense queries use QUERY with TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_MONTH or LAST_3_MONTHS.
            - Questions asking for an account, bank, UPI, cash or card balance use QUERY with ACCOUNT_BALANCE.
            - Questions about budget remaining, budget status or overspending use QUERY with CURRENT_STATUS.
            - Questions about card bills due, card reminders or upcoming card payments use QUERY with UPCOMING_DUE.
            """; }
}
