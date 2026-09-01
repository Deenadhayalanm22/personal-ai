package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;

final class FinanceInterpretationPrompt implements InterpretationPromptContributor {
    @Override public String instructions() { return """
            PERSONAL-FINANCE EVENT AND ROUTING
            Supported event type: EXPENSE.
            Core fields: amount, merchantName, sourceAccount, transactionDate (YYYY-MM-DD), rawText.
            - A new financial movement amount must have exact evidence in the current message.
            - Preserve a stated payment source as sourceAccount text. It is transaction metadata, not an account reference.
            - Money sent or paid to another person (for example mom, wife, friend, employee, or vendor) is EXPENSE
              unless both source and destination are explicitly the user's own accounts. The person is merchantName
              and the paying account is sourceAccount.
            - Temporal phrases such as today, yesterday, and "on Saturday" are dates, never account names. Keep them
              out of sourceAccount. Resolve a named weekday to the most recent occurrence in the user's timezone.
            - Editing or deleting earlier expenses is handled by the authenticated web dashboard, not conversation.
              Never reinterpret an edit, correction, removal, void, or deletion request as a new EXPENSE.
            - A pending expenseDetails message enriches the current candidate. A pending expenseCorrection message
              may explicitly correct it. Neither creates a second expense.
            """; }
}
