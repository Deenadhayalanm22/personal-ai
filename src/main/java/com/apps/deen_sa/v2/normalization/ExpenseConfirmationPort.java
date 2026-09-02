package com.apps.deen_sa.v2.normalization;

import com.apps.deen_sa.v2.dto.StoredDraftExtraction;

public interface ExpenseConfirmationPort {
    void requestConfirmation(StoredDraftExtraction extraction);
}
