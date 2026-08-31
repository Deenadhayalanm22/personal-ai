package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/web/dashboard")
public class WebDashboardController {
    private final WebAuthenticationService authentication;
    private final DashboardSummaryService summaries;
    private final MonthlyExpenseService monthlyExpenses;
    private final WebExpenseService expenses;

    public WebDashboardController(WebAuthenticationService authentication, DashboardSummaryService summaries,
                                  MonthlyExpenseService monthlyExpenses, WebExpenseService expenses) {
        this.authentication = authentication; this.summaries = summaries; this.monthlyExpenses = monthlyExpenses;
        this.expenses = expenses;
    }

    @GetMapping("/summary")
    public DashboardSummaryService.DashboardSummary summary(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        AppUserEntity user = authentication.authenticate(token);
        return summaries.summarize(user, selectedMonth(user, month));
    }

    /** Compatibility endpoint. New clients should load the section endpoints independently. */
    @GetMapping
    public LegacyDashboardResponse legacyDashboard(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = selectedMonth(user, month);
        return new LegacyDashboardResponse(monthlyExpenses.summarize(user, selected),
                expenses.list(user, selected, 10, null, new WebExpenseService.ExpenseFilter(null, null)));
    }

    private YearMonth selectedMonth(AppUserEntity user, YearMonth requested) {
        return requested == null ? YearMonth.now(ZoneId.of(user.getTimezone())) : requested;
    }

    public record LegacyDashboardResponse(MonthlyExpenseService.MonthlyExpenseResponse summary,
                                          WebExpenseService.ExpensePage recentExpenses) { }
}
