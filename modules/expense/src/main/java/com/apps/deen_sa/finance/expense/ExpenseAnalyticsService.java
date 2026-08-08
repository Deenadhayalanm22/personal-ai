package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseQuery;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.dto.TimeRange;
import com.apps.deen_sa.finance.persistence.FinanceAnalyticsRepository;
import org.springframework.stereotype.Service;

@Service
public class ExpenseAnalyticsService {

    private final FinanceAnalyticsRepository repo;

    public ExpenseAnalyticsService(FinanceAnalyticsRepository repo) {
        this.repo = repo;
    }

    public ExpenseSummary analyze(ExpenseQuery query) {

        ExpenseSummary summary = new ExpenseSummary();

        TimeRange range = query.getTimeRange();

        if (query.isIncludeTotal()) {
            summary.setTotalSpend(
                    repo.sumExpenses(
                            query.getUserId(),
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
                            query.getUserId(),
                            range,
                            query.getSourceAccount()
                    )
            );
            summary.setSpendBySubcategory(
                    repo.sumBySubcategory(
                            query.getUserId(),
                            range,
                            query.getCategory(),
                            query.getSourceAccount()
                    )
            );
        }

        if (query.isGroupBySourceAccount()) {
            summary.setSpendBySourceAccount(
                    repo.sumBySourceAccount(
                            query.getUserId(),
                            query.getTimeRange(),
                            query.getCategory()
                    )
            );
        }

        return summary;
    }
}
