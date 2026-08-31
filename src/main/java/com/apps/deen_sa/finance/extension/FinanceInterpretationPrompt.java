package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;

final class FinanceInterpretationPrompt implements InterpretationPromptContributor {
    @Override public String instructions() { return """
            PERSONAL-FINANCE EXTENSION
            Supported event type: EXPENSE.
            Fields: amount, category, subcategory, merchantName, sourceAccount, transactionDate (YYYY-MM-DD),
            taxonomyCandidate, rawText.
            - A new financial movement amount must have exact evidence in the current message.
            - Preserve a stated payment source as sourceAccount text. It is transaction metadata, not an account reference.
            - Money sent or paid to another person (for example mom, wife, friend, employee, or vendor) is EXPENSE
              unless both source and destination are explicitly the user's own accounts. The person is merchantName
              and the paying account is sourceAccount.
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
            - Editing or deleting earlier expenses is handled by the authenticated web dashboard, not conversation.
              Never reinterpret an edit, correction, removal, void, or deletion request as a new EXPENSE.
            - Expense queries use QUERY with TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_MONTH, LAST_7_DAYS or LAST_3_MONTHS.
            """; }
}
