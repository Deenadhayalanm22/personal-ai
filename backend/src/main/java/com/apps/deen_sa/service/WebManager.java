package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.exception.WebApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;

/** Cross-stack API façade for FIN-EPIC-002 through FIN-EPIC-005. See docs/jira/personal-expense/. */
@Service
public class WebManager {
    private final WebAuthenticationService authentication;
    private final WebLoginRequestService loginRequests;
    private final WebExpenseTaxonomyService taxonomy;
    private final WebUserReferencePreferenceService referencePreferences;
    private final WebReferenceMergeService referenceMerges;
    private final MonthlyFinancialTransactionService monthlyTransactions;
    private final FinancialTransactionListService transactionList;
    private final FinancialTransactionCalendarService transactionCalendar;
    private final ExpenseEditOptionsService editOptions;
    private final FinancialTransactionEditService transactionEditor;
    private final PendingActionContextService actionContexts;
    private final WebLoanService loans;
    private final WebMutualFundService mutualFunds;
    private final MfApiService mfApi;
    private final ActionManagementService actions;

    @Autowired
    public WebManager(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
                      WebExpenseTaxonomyService taxonomy, WebUserReferencePreferenceService referencePreferences,
                      WebReferenceMergeService referenceMerges, MonthlyFinancialTransactionService monthlyTransactions,
                      FinancialTransactionListService transactionList,
                      FinancialTransactionCalendarService transactionCalendar, ExpenseEditOptionsService editOptions,
                      FinancialTransactionEditService transactionEditor, PendingActionContextService actionContexts,
                      WebLoanService loans, WebMutualFundService mutualFunds, MfApiService mfApi,
                      ActionManagementService actions) {
        this.authentication = authentication;
        this.loginRequests = loginRequests;
        this.taxonomy = taxonomy;
        this.referencePreferences = referencePreferences;
        this.referenceMerges = referenceMerges;
        this.monthlyTransactions = monthlyTransactions;
        this.transactionList = transactionList;
        this.transactionCalendar = transactionCalendar;
        this.editOptions = editOptions;
        this.transactionEditor = transactionEditor;
        this.actionContexts = actionContexts;
        this.loans = loans;
        this.mutualFunds = mutualFunds;
        this.mfApi = mfApi;
        this.actions = actions;
    }

    /** Retained for focused web-controller tests that do not exercise loans. */
    public WebManager(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
                      WebExpenseTaxonomyService taxonomy, WebUserReferencePreferenceService referencePreferences,
                      WebReferenceMergeService referenceMerges, MonthlyFinancialTransactionService monthlyTransactions,
                      FinancialTransactionListService transactionList,
                      FinancialTransactionCalendarService transactionCalendar, ExpenseEditOptionsService editOptions,
                      FinancialTransactionEditService transactionEditor, PendingActionContextService actionContexts) {
        this(authentication, loginRequests, taxonomy, referencePreferences, referenceMerges, monthlyTransactions,
                transactionList, transactionCalendar, editOptions, transactionEditor, actionContexts, null, null, null, null);
    }

    public void requestLoginLink(String phoneNumber, String clientAddress) {
        loginRequests.request(phoneNumber, clientAddress);
    }

    public WebAuthenticationService.SessionGrant exchange(String token) { return authentication.exchange(token); }
    public void validateSession(String token) { authentication.authenticate(token); }
    public void logout(String token) { authentication.logout(token); }
    public WebAuthenticationService.DemoProfile demoProfile(String token) { return authentication.demoProfile(token); }
    public WebAuthenticationService.DemoProfile setDemoMode(String token, boolean enabled) {
        return authentication.setDemoMode(token, enabled);
    }

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

    public WebReferenceMergeService.MergeResponse mergeReferences(
            String token, WebReferenceMergeService.MergeRequest request) {
        return referenceMerges.merge(authentication.authenticate(token), request);
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

    public WebLoanService.LoanResponse createLoan(String token, WebLoanService.LoanCreateRequest request) {
        return loans.create(authentication.authenticate(token), request);
    }

    public WebLoanService.LoanListResponse loans(String token) {
        return loans.list(authentication.authenticate(token));
    }

    public WebLoanService.LoanResponse updateLoan(
            String token, Long id, WebLoanService.LoanUpdateRequest request) {
        return loans.update(authentication.authenticate(token), id, request);
    }

    public ActionManagementService.ActionListResponse actions(String token) {
        return actions.openActions(authentication.authenticate(token));
    }

    public ActionManagementService.ActionResponse completeAction(String token, Long id) {
        return actions.complete(authentication.authenticate(token), id);
    }

    public WebMutualFundService.MutualFundResponse createMutualFund(String token, WebMutualFundService.MutualFundCreateRequest request) {
        return mutualFunds.create(authentication.authenticate(token), request);
    }

    public java.util.List<MfApiService.SchemeSearchResult> searchMutualFunds(String token, String query) {
        authentication.authenticate(token);
        return mfApi.search(query);
    }

    public WebMutualFundService.MutualFundListResponse mutualFunds(String token) {
        return mutualFunds.list(authentication.authenticate(token));
    }

    public WebMutualFundService.MutualFundDetailResponse mutualFund(String token, Long id) {
        return mutualFunds.detail(authentication.authenticate(token), id);
    }

    public WebMutualFundService.TransactionResponse addMutualFundLumpSum(String token, Long id,
                                                                           WebMutualFundService.LumpSumRequest request) {
        return mutualFunds.addLumpSum(authentication.authenticate(token), id, request);
    }

    public WebMutualFundService.TransactionResponse confirmMutualFundSip(String token, Long id, YearMonth month,
                                                                           WebMutualFundService.LumpSumRequest request) {
        return mutualFunds.confirmSip(authentication.authenticate(token), id, month, request);
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
    public record DemoModeRequest(boolean enabled) { }
    public record UserReferenceEntityTypesResponse(List<UserReferenceEntityType> entityTypes) { }
}
