package com.apps.deen_sa.service;

import com.apps.deen_sa.repo.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseSummaryService {

    private final ExpenseRepository repository;

    public ExpenseSummaryService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public Map<String, BigDecimal> getCurrentMonthTotals() {

        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate()
                .atStartOfDay()
                .atOffset(now.getOffset());

        OffsetDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        List<Object[]> rows =
                repository.getMonthlyTotalsByCategory(startOfMonth, startOfNextMonth);

        Map<String, BigDecimal> result = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String category = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            result.put(category, total);
        }

        return result;
    }
}
