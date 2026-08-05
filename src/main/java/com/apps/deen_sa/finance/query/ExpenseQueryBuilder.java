package com.apps.deen_sa.finance.query;

import com.apps.deen_sa.dto.ExpenseQuery;
import com.apps.deen_sa.dto.QueryResult;
import org.springframework.stereotype.Component;

@Component
public class ExpenseQueryBuilder {

    private final TimeRangeResolver timeRangeResolver;

    public ExpenseQueryBuilder(TimeRangeResolver timeRangeResolver) {
        this.timeRangeResolver = timeRangeResolver;
    }

    public ExpenseQuery from(QueryResult qr, Long userId) {

        ExpenseQuery eq = new ExpenseQuery();
        eq.setUserId(userId.toString());

        eq.setTimeRange(
                timeRangeResolver.resolve(qr.getTimePeriod())
        );

        eq.setCategory(qr.getCategory());
        eq.setSourceAccount(qr.getSourceAccount());

        eq.setIncludeTotal(qr.isIncludeTotal());
        // A category breakdown makes total queries useful for insights as well.
        eq.setGroupByCategory(qr.isGroupByCategory() || qr.isIncludeTotal());
        eq.setGroupBySourceAccount(qr.isGroupBySourceAccount());

        return eq;
    }
}
