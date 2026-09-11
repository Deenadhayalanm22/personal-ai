package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.exception.WebApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebManager {
    private final WebAuthenticationService authentication;
    private final WebLoginRequestService loginRequests;
    private final WebExpenseTaxonomyService taxonomy;
    private final WebUserReferencePreferenceService referencePreferences;
    private final MonthlyFinancialTransactionService monthlyTransactions;
    private final FinancialTransactionListService transactionList;
    private final FinancialTransactionCalendarService transactionCalendar;
    private final ExpenseEditOptionsService editOptions;
    private final FinancialTransactionEditService transactionEditor;
    private final PendingActionContextService actionContexts;

    public void requestLoginLink(String phoneNumber, String clientAddress) {
        loginRequests.request(phoneNumber, clientAddress);
    }

    public WebAuthenticationService.SessionGrant exchange(String token) { return authentication.exchange(token); }
    public void validateSession(String token) { authentication.authenticate(token); }
    public void logout(String token) { authentication.logout(token); }

    public WebExpenseTaxonomyService.TaxonomyResponse expenseTaxonomy(String token) {
        authentication.authenticate(token);
        return taxonomy.options();
    }

    public UserReferenceEntityTypesResponse referenceEntityTypes(String token) {
        authentication.authenticate(token);
        return new UserReferenceEntityTypesResponse(List.of(UserReferenceEntityType.values()));
    }

    public WebUserReferencePreferenceService.UserReferencePreferenceResponse createReferencePreference(
            String token, WebUserReferencePreferenceService.UserReferencePreferenceRequest request) {
        return referencePreferences.create(authentication.authenticate(token), request);
    }

    public WebUserReferencePreferenceService.UserReferencePreferenceListResponse referencePreferences(String token) {
        return referencePreferences.list(authentication.authenticate(token));
    }

    public MoneyStoriesService.MonthlyStoriesApiResponse monthlyExpenses(String token, YearMonth month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = month == null ? YearMonth.now(ZoneId.of(user.getTimezone())) : month;
        return monthlyTransactions.monthlyStories(user, selected);
    }

    public FinancialTransactionListService.ExpensePage expenses(
            String token, YearMonth month, LocalDate date, int limit, Long beforeId,
            String category, String subcategory) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = month == null ? YearMonth.now(ZoneId.of(user.getTimezone())) : month;
        return transactionList.list(user, selected, limit, beforeId,
                new FinancialTransactionListService.ExpenseFilter(category, subcategory, date));
    }

    public FinancialTransactionCalendarService.CalendarResponse expenseCalendar(String token, String month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = parseCalendarMonth(month);
        try {
            return transactionCalendar.calendar(user, selected);
        } catch (RuntimeException failure) {
            throw new WebApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CALENDAR_FETCH_FAILED",
                    "Unable to load the expense calendar.");
        }
    }

    public ExpenseEditOptionsService.ExpenseEditOptions expenseEditOptions(String token) {
        return editOptions.options(authentication.authenticate(token));
    }

    public FinancialTransactionListService.ExpenseItem editExpense(
            String token, Long id, FinancialTransactionEditService.ExpenseUpdate request) {
        return transactionEditor.edit(authentication.authenticate(token), id, request);
    }

    public void deleteExpense(String token, Long id) {
        transactionEditor.delete(authentication.authenticate(token), id);
    }

    public PendingActionContextService.ContextResponse createPendingActionContext(
            String token, PendingActionContextService.ContextRequest request) {
        return actionContexts.create(authentication.authenticate(token).getId(), request);
    }

    private YearMonth parseCalendarMonth(String value) {
        if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])")) throw invalidMonth();
        try {
            return YearMonth.parse(value);
        } catch (DateTimeException invalid) {
            throw invalidMonth();
        }
    }

    private WebApiException invalidMonth() {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "month must use YYYY-MM format");
    }

    public record LoginLinkRequest(String phoneNumber) { }
    public record LoginLinkResponse(String message) { }
    public record MagicLinkRequest(String token) { }
    public record AuthResponse(boolean authenticated, Instant expiresAt) { }
    public record UserReferenceEntityTypesResponse(List<UserReferenceEntityType> entityTypes) { }
}
