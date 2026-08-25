package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/web/dashboard")
public class WebDashboardController {
    private final WebAuthenticationService authentication;
    private final DashboardSummaryService summaries;
    private final MonthlyExpenseService monthlyExpenses;
    private final DashboardAccountService accounts;
    private final WebExpenseService expenses;
    private final DashboardEnrichmentService enrichment;

    public WebDashboardController(WebAuthenticationService authentication, DashboardSummaryService summaries,
                                  MonthlyExpenseService monthlyExpenses, DashboardAccountService accounts,
                                  WebExpenseService expenses, DashboardEnrichmentService enrichment) {
        this.authentication = authentication; this.summaries = summaries; this.monthlyExpenses = monthlyExpenses;
        this.accounts = accounts; this.expenses = expenses; this.enrichment = enrichment;
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
                accounts.activeAccounts(user.getId(), ZoneId.of(user.getTimezone())),
                expenses.list(user, selected, 10, null, new WebExpenseService.ExpenseFilter(null, null, null)),
                enrichment.queue(user.getId()));
    }

    private YearMonth selectedMonth(AppUserEntity user, YearMonth requested) {
        return requested == null ? YearMonth.now(ZoneId.of(user.getTimezone())) : requested;
    }

    public record LegacyDashboardResponse(MonthlyExpenseService.MonthlyExpenseResponse summary,
                                          List<DashboardAccountService.DashboardAccount> accounts,
                                          WebExpenseService.ExpensePage recentExpenses,
                                          DashboardEnrichmentService.EnrichmentQueue needsEnrichment) { }
}
