package com.apps.deen_sa.v2.controller;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.service.MonthlyFinancialTransactionService;
import com.apps.deen_sa.v2.service.FinancialTransactionListService;
import com.apps.deen_sa.v2.service.FinancialTransactionCalendarService;
import com.apps.deen_sa.v2.service.ExpenseEditOptionsService;
import com.apps.deen_sa.web.WebApiException;
import org.springframework.http.HttpStatus;
import com.apps.deen_sa.web.WebAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.DateTimeException;

@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebFinanceV2Controller {
    private static final String SESSION_COOKIE = "WEB_SESSION";

    private final WebAuthenticationService authentication;
    private final MonthlyFinancialTransactionService monthlyTransactions;
    private final FinancialTransactionListService transactionList;
    private final FinancialTransactionCalendarService transactionCalendar;
    private final ExpenseEditOptionsService editOptions;

    @GetMapping("/expenses/monthly")
    public MonthlyFinancialTransactionService.MonthlyExpenseResponse monthlyExpenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month
    ) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = month == null
                ? YearMonth.now(ZoneId.of(user.getTimezone()))
                : month;
        return monthlyTransactions.summarize(user, selected);
    }

    @GetMapping("/expenses")
    public FinancialTransactionListService.ExpensePage expenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory
    ) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = month == null
                ? YearMonth.now(ZoneId.of(user.getTimezone()))
                : month;
        return transactionList.list(
                user, selected, limit, beforeId,
                new FinancialTransactionListService.ExpenseFilter(
                        category, subcategory, date));
    }

    @GetMapping("/expenses/calendar")
    public FinancialTransactionCalendarService.CalendarResponse expenseCalendar(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam String month
    ) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = parseCalendarMonth(month);
        try {
            return transactionCalendar.calendar(user, selected);
        } catch (RuntimeException failure) {
            throw new WebApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CALENDAR_FETCH_FAILED",
                    "Unable to load the expense calendar.");
        }
    }

    @GetMapping("/expenses/options")
    public ExpenseEditOptionsService.ExpenseEditOptions expenseEditOptions(
            @CookieValue(name = SESSION_COOKIE, required = false) String token
    ) {
        AppUserEntity user = authentication.authenticate(token);
        return editOptions.options(user);
    }

    private YearMonth parseCalendarMonth(String value) {
        if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new WebApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MONTH",
                    "month must use YYYY-MM format");
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeException invalid) {
            throw new WebApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MONTH",
                    "month must use YYYY-MM format");
        }
    }
}
