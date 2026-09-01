package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;

final class FinanceQueryPrompt implements InterpretationPromptContributor {
    @Override public String instructions() { return """
            PERSONAL-FINANCE QUERIES
            Expense queries use QUERY with TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_MONTH, LAST_7_DAYS,
            or LAST_3_MONTHS. Query interpretation never creates or mutates an EXPENSE event.
            """; }
}
