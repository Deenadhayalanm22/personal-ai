package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/** Loads only the headline data needed to render the dashboard shell. */
@Service
public class DashboardSummaryService {
    private final StateChangeRepository expenses;

    public DashboardSummaryService(StateChangeRepository expenses) { this.expenses = expenses; }

    public DashboardSummary summarize(AppUserEntity user, YearMonth month) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<Object[]> rows = expenses.summarizeActiveExpensesForPeriod(
                user.getId().toString(), start, end);
        Object[] row = rows.isEmpty() ? new Object[]{0L, BigDecimal.ZERO} : rows.getFirst();
        return new DashboardSummary(month.toString(), user.getCurrency(),
                (BigDecimal) row[1], ((Number) row[0]).longValue());
    }

    public record DashboardSummary(String month, String currency, BigDecimal totalSpend,
                                   long transactionCount) { }
}
