package com.apps.deen_sa.normalization;

import com.apps.deen_sa.dto.StoredDraftExtraction;

public interface ExpenseConfirmationPort {
    void requestConfirmation(StoredDraftExtraction extraction);

    void sendCaptureInstruction(String externalUserId, String message);
}
