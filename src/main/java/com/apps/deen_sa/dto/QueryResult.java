package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryResult {

    // intent routing
    private String intent;          // always "QUERY"
    private String queryType;       // EXPENSE_SUMMARY | EXPENSE_TOTAL

    // time
    private String timePeriod;      // THIS_MONTH, LAST_MONTH, etc.

    // optional filters
    private String category;
    private String subcategory;
    private String merchant;
    private String sourceAccount;
    private String tag;

    // aggregation flags
    private boolean includeTotal;
    private boolean groupByCategory;
    private boolean groupBySourceAccount;

    private double confidence;
}

