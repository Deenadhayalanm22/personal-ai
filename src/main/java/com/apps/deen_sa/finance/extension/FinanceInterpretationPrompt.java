package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;

final class FinanceInterpretationPrompt implements InterpretationPromptContributor {
    @Override public String instructions() { return """
            PERSONAL-FINANCE EXTENSION
            Supported event types: EXPENSE, INCOME, TRANSFER, LIABILITY_PAYMENT, ACCOUNT_SETUP, BUDGET_SET.
            Fields: amount, category, subcategory, merchantName, sourceAccount, destinationAccount, sourceBalance,
            creditLimit, creditCardBillingDay (1-31), creditCardDueDay (1-31), transactionDate (YYYY-MM-DD),
            taxonomyCandidate, rawText.
            - A new financial movement amount must have exact evidence in the current message.
            - Existing accounts are reference candidates only. Populate an account only when stated now or when it
              directly answers a pending question.
            - UPI, bank transfer, debit card, FASTag, and bank auto-debit map to BANK_ACCOUNT; cash maps to CASH;
              credit card/card EMI maps to CREDIT_CARD; wallet maps to WALLET.
            - ACCOUNT_SETUP attributes such as balance, outstanding, limit, billing day and due day are not transaction amounts.
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
            - When the user bought or purchased an identifiable physical item: use Electronics for electronics,
              Clothing for clothing/footwear/wearable accessories, Gifts only when explicitly bought as a gift, and
              Education or Medical when clearly applicable. Otherwise use Shopping / Other Shopping. An unfamiliar
              product name is not a reason to use Miscellaneous. Examples: beach mat, storage box, umbrella, suitcase,
              water bottle, and yoga mat are Shopping / Other Shopping.
            - Travel is for out-of-town journeys: hotels/OYO/resorts/hostels/homestays are Accommodation; airfare is
              Flights; long-distance train or bus travel is Intercity Transport; sightseeing and tour experiences are
              Travel Activities; visa and passport charges are Travel Documents & Fees. Transportation remains for
              everyday fuel, vehicle maintenance, parking, tolls, and local commuting.
            - Miscellaneous is a last resort for expenses that are not purchases of identifiable physical goods.
            - If the broad category is clear but no configured specific subcategory fits, taxonomyCandidate may
              propose a reusable category/subcategory for later human review. It never replaces the configured
              category/subcategory. Do not propose merchants, brands, people, locations, or one-off descriptions.
            - "Set my monthly X budget to Y" is BUDGET_SET: amount=Y and category=X.
            - Editing or deleting earlier expenses is handled by the authenticated web dashboard, not conversation.
              Never reinterpret an edit, correction, removal, void, or deletion request as a new EXPENSE.
            - Expense queries use QUERY with TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_MONTH, LAST_7_DAYS or LAST_3_MONTHS.
            - Questions asking for an account, bank, UPI, cash or card balance use QUERY with ACCOUNT_BALANCE.
            - Questions about budget remaining, budget status or overspending use QUERY with CURRENT_STATUS.
            - Questions about card bills due, card reminders or upcoming card payments use QUERY with UPCOMING_DUE.
            """; }
}
