package com.apps.deen_sa.resolver;

import com.apps.deen_sa.dto.ExpenseQuery;
import com.apps.deen_sa.dto.QueryResult;
import org.springframework.stereotype.Component;

@Component
public class ExpenseQueryBuilder {

    private final TimeRangeResolver timeRangeResolver;

    public ExpenseQueryBuilder(TimeRangeResolver timeRangeResolver) {
        this.timeRangeResolver = timeRangeResolver;
    }

    public ExpenseQuery from(QueryResult qr) {

        ExpenseQuery eq = new ExpenseQuery();

        eq.setTimeRange(
                timeRangeResolver.resolve(qr.getTimePeriod())
        );

        eq.setCategory(qr.getCategory());
        eq.setSourceAccount(qr.getSourceAccount());

        eq.setIncludeTotal(qr.isIncludeTotal());
        eq.setGroupByCategory(qr.isGroupByCategory());
        eq.setGroupBySourceAccount(qr.isGroupBySourceAccount());

        return eq;
    }
}
