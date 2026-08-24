package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MonthlyExpenseService {
    private final StateChangeRepository expenses;

    public MonthlyExpenseService(StateChangeRepository expenses) { this.expenses = expenses; }

    public MonthlyExpenseResponse summarize(AppUserEntity user, YearMonth month) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        expenses.sumExpensesByCategoryForPeriod(String.valueOf(user.getId()), start, end)
                .forEach(row -> categories.put((String) row[0], (BigDecimal) row[1]));
        BigDecimal total = categories.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MonthlyExpenseResponse(month.toString(), user.getCurrency(), total, categories);
    }

    public record MonthlyExpenseResponse(String month, String currency, BigDecimal total,
                                         Map<String, BigDecimal> categories) { }
}
