package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseQuery;
import com.apps.deen_sa.dto.ExpenseSummary;
import com.apps.deen_sa.dto.TimeRange;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExpenseAnalyticsService {

    private final StateChangeRepository repo;

    public ExpenseAnalyticsService(StateChangeRepository repo) {
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
                            query.getCategory()
                    )
            );
        }

        if (query.isGroupByCategory()) {
            summary.setSpendByCategory(
                    rows(repo.sumByCategory(query.getUserId(), range.start(), range.end()))
            );
            summary.setSpendBySubcategory(
                    rows(repo.sumBySubcategory(query.getUserId(), range.start(), range.end(), query.getCategory()))
            );
        }

        return summary;
    }

    private Map<String, BigDecimal> rows(List<Object[]> rows) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put((String) row[0], (BigDecimal) row[1]));
        return values;
    }
}
