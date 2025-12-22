package com.apps.deen_sa.service;

import com.apps.deen_sa.dto.ExpenseQuery;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.dto.TimeRange;
import com.apps.deen_sa.repo.TransactionRepository;
import org.springframework.stereotype.Service;

@Service
public class ExpenseAnalyticsService {

    private final TransactionRepository repo;

    public ExpenseAnalyticsService(TransactionRepository repo) {
        this.repo = repo;
    }

    public ExpenseSummary analyze(ExpenseQuery query) {

        ExpenseSummary summary = new ExpenseSummary();

        TimeRange range = query.getTimeRange();

        if (query.isIncludeTotal()) {
            summary.setTotalSpend(
                    repo.sumExpenses(
                            range.start(),
                            range.end(),
                            query.getCategory(),
                            query.getSourceAccount()
                    )
            );
        }

        if (query.isGroupByCategory()) {
            summary.setSpendByCategory(
                    repo.sumByCategory(
                            range,
                            query.getSourceAccount()
                    )
            );
        }

        if (query.isGroupBySourceAccount()) {
            summary.setSpendBySourceAccount(
                    repo.sumBySourceAccount(
                            query.getTimeRange(),
                            query.getCategory()
                    )
            );
        }

        return summary;
    }
}
