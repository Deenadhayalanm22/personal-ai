package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;

record CorrectionOutcome(StateChangeEntity original, StateChangeEntity replacement, String balanceImpact) {
    boolean deleted() { return replacement == null; }
}
