package com.apps.deen_sa.finance.expense.correction;

import lombok.Data;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExpenseCorrectionState {
    private CorrectionStage stage = CorrectionStage.BROWSING;
    private CorrectionAction action;
    private Long beforeId;
    private Long selectedTransactionId;
    private CorrectionField field;
    private String proposedValue;
    private String searchTerm;
    private Instant periodStart;
    private Instant periodEnd;
    private List<Long> visibleTransactionIds = new ArrayList<>();
}
