package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import java.util.List;

record ExpenseBrowsePage(List<StateChangeEntity> transactions, Long nextCursor) {
    boolean hasMore() { return nextCursor != null; }
}
