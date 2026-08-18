package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpenseQuery {
    // owner scope
    private String userId;

    // mandatory
    private TimeRange timeRange;

    // optional filters
    private String category;
    private String sourceAccount;

    // aggregation flags
    private boolean includeTotal;
    private boolean groupByCategory;
    private boolean groupBySourceAccount;
}
