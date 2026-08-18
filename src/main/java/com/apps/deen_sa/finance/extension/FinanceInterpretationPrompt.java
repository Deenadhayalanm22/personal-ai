package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;

final class FinanceInterpretationPrompt implements InterpretationPromptContributor {
    @Override public String instructions() { return """
            PERSONAL-FINANCE EXTENSION
            Supported event types: EXPENSE, EXPENSE_CORRECTION, INCOME, TRANSFER, LIABILITY_PAYMENT, ACCOUNT_SETUP, BUDGET_SET.
            Fields: amount, category, subcategory, merchantName, sourceAccount, destinationAccount, sourceBalance,
            creditLimit, creditCardDueDay (1-31), transactionDate (YYYY-MM-DD), tags, rawText, correctionChoice.
            - A new financial movement amount must have exact evidence in the current message.
            - Existing accounts are reference candidates only. Populate an account only when stated now or when it
              directly answers a pending question.
            - UPI, bank transfer, debit card, FASTag, and bank auto-debit map to BANK_ACCOUNT; cash maps to CASH;
              credit card/card EMI maps to CREDIT_CARD; wallet maps to WALLET.
            - ACCOUNT_SETUP attributes such as balance, outstanding, limit and due day are not transaction amounts.
              Preserve the complete current message in rawText for deterministic extraction by the capability.
            - Incoming salary, receipts, refunds, interest and gifts are INCOME; a receiving account is destinationAccount.
            - Paying a credit-card bill is LIABILITY_PAYMENT. Buying goods using a card remains EXPENSE.
            - Money sent or paid to another person (for example mom, wife, friend, employee, or vendor) is EXPENSE
              unless both source and destination are explicitly the user's own accounts. The person is merchantName
              and the paying account is sourceAccount.
            - TRANSFER is only movement between two explicitly owned accounts. Do not use TRANSFER for gifts,
              family support, purchases, bills, or payments to another person.
            - Within Food & Dining, restaurant, cafe, bar, office/team lunch, dining out, and prepared-meal delivery
              are Eating Out. Snacks & Beverages is for a snack, tea, coffee, or beverage purchase that is not a meal.
              Celebration Meal/Home Cooked requires evidence of a celebration meal or food cooked at home; a family
              dinner at a restaurant remains Eating Out.
            - Use a broad category and a specific subcategory. Never invent a date, account, amount or classification.
            - Temporal phrases such as today, yesterday, and "on Saturday" are dates, never account names. Keep them
              out of sourceAccount. Resolve a named weekday to the most recent occurrence in the user's timezone.
            - School bags, books, stationery, and classroom materials are School Supplies, not School Fees.
              Ordinary meat, fish, chicken, vegetables, and ingredients bought for cooking are Groceries. Use
              Celebration Meal/Home Cooked only when the message explicitly describes a celebration or special meal.
            - "Set my monthly X budget to Y" is BUDGET_SET: amount=Y and category=X.
            - Requests to find, edit, correct, remove, void, or delete an earlier expense are EXPENSE_CORRECTION.
              Never reinterpret them as a new EXPENSE. During its pending flow, put the user's literal answer in
              correctionChoice; the capability performs selection, validation, and confirmation deterministically.
            - Expense queries use QUERY with TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_MONTH or LAST_3_MONTHS.
            - Questions asking for an account, bank, UPI, cash or card balance use QUERY with ACCOUNT_BALANCE.
            - Questions about budget remaining, budget status or overspending use QUERY with CURRENT_STATUS.
            - Questions about card bills due, card reminders or upcoming card payments use QUERY with UPCOMING_DUE.
            """; }
}
