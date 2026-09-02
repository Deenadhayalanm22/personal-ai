package com.apps.deen_sa.v2.controller;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.service.MonthlyFinancialTransactionService;
import com.apps.deen_sa.v2.service.FinancialTransactionListService;
import com.apps.deen_sa.v2.service.FinancialTransactionCalendarService;
import com.apps.deen_sa.v2.service.ExpenseEditOptionsService;
import com.apps.deen_sa.v2.service.FinancialTransactionEditService;
import com.apps.deen_sa.conversation.context.PendingActionContextService;
import com.apps.deen_sa.web.WebApiException;
import org.springframework.http.HttpStatus;
import com.apps.deen_sa.web.WebAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;

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
    private final FinancialTransactionEditService transactionEditor;
    private final PendingActionContextService actionContexts;

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

    @PatchMapping("/expenses/{id}")
    public FinancialTransactionListService.ExpenseItem editExpense(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id,
            @RequestBody FinancialTransactionEditService.ExpenseUpdate request
    ) {
        AppUserEntity user = authentication.authenticate(token);
        return transactionEditor.edit(user, id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id
    ) {
        AppUserEntity user = authentication.authenticate(token);
        transactionEditor.delete(user, id);
    }

    @PostMapping("/expenses/calendar/context")
    @ResponseStatus(HttpStatus.CREATED)
    public PendingActionContextService.ContextResponse createPendingActionContext(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody PendingActionContextService.ContextRequest request
    ) {
        AppUserEntity user = authentication.authenticate(token);
        return actionContexts.create(user.getId(), request);
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
