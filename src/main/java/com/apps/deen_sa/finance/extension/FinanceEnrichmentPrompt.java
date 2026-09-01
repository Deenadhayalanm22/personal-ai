package com.apps.deen_sa.finance.extension;

import com.apps.deen_sa.extension.api.InterpretationPromptContributor;

final class FinanceEnrichmentPrompt implements InterpretationPromptContributor {
    @Override public String instructions() { return """
            PERSONAL-FINANCE OPTIONAL ENRICHMENT
            Optional details keys: beneficiary, purpose, occasion, plannedStatus, reimbursable, tripContext.
            - Extract several optional values from one natural sentence when each has exact current-message evidence.
            - plannedStatus is PLANNED or UNPLANNED. reimbursable is true or false only when explicitly stated.
            - sourceAccount may also be enriched when explicitly named.
            - Never infer beneficiary, purpose, occasion, planned status, reimbursable status, trip/event context, or account.
            - Missing optional context never invalidates an otherwise valid expense and never determines follow-up UX.
            - During expenseDetails, return optional enrichment only; preserve amount, date, merchant and classification.
            """; }
}
