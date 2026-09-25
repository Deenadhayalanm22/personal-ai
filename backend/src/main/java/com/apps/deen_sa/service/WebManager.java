package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.exception.WebApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;

/**
 * Cross-stack API façade for FIN-EPIC-002 through FIN-EPIC-005. See docs/jira/personal-expense/.
 */
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
    private final WebStockService stocks;
    private final StockMarketDataAdapter stockMarketData;
    private final ActionManagementService actions;
    private final WebIncomeService income;
    private final WebRecurringCommitmentService commitments;
    private final WebCreditCardService creditCards;
    @Autowired private CommitmentSavingsService savings;

    @Autowired
    public WebManager(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
                      WebExpenseTaxonomyService taxonomy, WebUserReferencePreferenceService referencePreferences,
                      WebReferenceMergeService referenceMerges, MonthlyFinancialTransactionService monthlyTransactions,
                      FinancialTransactionListService transactionList,
                      FinancialTransactionCalendarService transactionCalendar, ExpenseEditOptionsService editOptions,
                      FinancialTransactionEditService transactionEditor, PendingActionContextService actionContexts,
                      WebLoanService loans, WebMutualFundService mutualFunds, MfApiService mfApi,
                      ActionManagementService actions, WebStockService stocks, StockMarketDataAdapter stockMarketData, WebIncomeService income,
                      WebRecurringCommitmentService commitments, WebCreditCardService creditCards) {
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
        this.stocks = stocks;
        this.stockMarketData = stockMarketData;
        this.income = income;
        this.commitments = commitments;
        this.creditCards = creditCards;
    }

    /**
     * Retained for focused web-controller tests that do not exercise loans.
     */
    public WebManager(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
                      WebExpenseTaxonomyService taxonomy, WebUserReferencePreferenceService referencePreferences,
                      WebReferenceMergeService referenceMerges, MonthlyFinancialTransactionService monthlyTransactions,
                      FinancialTransactionListService transactionList,
                      FinancialTransactionCalendarService transactionCalendar, ExpenseEditOptionsService editOptions,
                      FinancialTransactionEditService transactionEditor, PendingActionContextService actionContexts) {
        this(authentication, loginRequests, taxonomy, referencePreferences, referenceMerges, monthlyTransactions,
                transactionList, transactionCalendar, editOptions, transactionEditor, actionContexts, null, null, null, null, null, null, null, null, null);
    }

    public void requestLoginLink(String phoneNumber, String clientAddress) {
        loginRequests.request(phoneNumber, clientAddress);
    }

    public WebAuthenticationService.SessionGrant exchange(String token) {
        return authentication.exchange(token);
    }

    public void validateSession(String token) {
        authentication.authenticate(token);
    }

    public void logout(String token) {
        authentication.logout(token);
    }

    public WebAuthenticationService.DemoProfile demoProfile(String token) {
        return authentication.demoProfile(token);
    }

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

    public WebRecurringCommitmentService.CommitmentListResponse commitments(String token) { return commitments.list(authentication.authenticate(token)); }
    public WebRecurringCommitmentService.CommitmentHistoryResponse commitmentHistory(String token, Long id) { return commitments.history(authentication.authenticate(token), id); }
    public WebRecurringCommitmentService.CommitmentResponse createCommitment(String token, WebRecurringCommitmentService.CommitmentRequest request) { return commitments.create(authentication.authenticate(token), request); }
    public WebRecurringCommitmentService.CommitmentResponse updateCommitment(String token, Long id, WebRecurringCommitmentService.CommitmentRequest request) { return commitments.update(authentication.authenticate(token), id, request); }
    public void deleteCommitment(String token, Long id) { commitments.delete(authentication.authenticate(token), id); }
    public WebRecurringCommitmentService.OccurrenceResponse completeCommitment(String token, Long id, YearMonth month) { return commitments.markDone(authentication.authenticate(token), id, month.toString()); }
    public WebRecurringCommitmentService.OccurrenceResponse completeCommitment(String token, Long id, WebRecurringCommitmentService.CompletionRequest request) { return commitments.complete(authentication.authenticate(token), id, request); }
    public WebRecurringCommitmentService.OccurrenceResponse skipCommitment(String token, Long id, YearMonth month) { return commitments.skip(authentication.authenticate(token), id, month.toString()); }
    public WebRecurringCommitmentService.OccurrenceResponse addCommitmentExtra(String token, Long id, YearMonth month, WebRecurringCommitmentService.ExtraRequest request) { return commitments.addExtra(authentication.authenticate(token), id, month.toString(), request); }
    public WebRecurringCommitmentService.CommitmentReviewResponse commitmentReview(String token, YearMonth month) { return commitments.review(authentication.authenticate(token), month == null ? null : month.toString()); }
    public void resolveCommitmentReview(String token, Long id, WebRecurringCommitmentService.ResolveRequest request) { commitments.resolve(authentication.authenticate(token), id, request); }
    public CommitmentSavingsService.PlanView savingsPreview(String token, Long id) { return savings.preview(authentication.authenticate(token), id); }
    public List<CommitmentSavingsService.PlanView> savingsPlans(String token) { return savings.list(authentication.authenticate(token)); }
    public CommitmentSavingsService.PlanView savingsPlan(String token, Long id) { return savings.get(authentication.authenticate(token), id); }
    public CommitmentSavingsService.PlanView createSavingsPlan(String token, Long id, CommitmentSavingsService.CreateRequest request) { return savings.create(authentication.authenticate(token), id, request); }
    public CommitmentSavingsService.PlanView recordSavings(String token, Long id, CommitmentSavingsService.EntryRequest request) { return savings.record(authentication.authenticate(token), id, request); }
    public CommitmentSavingsService.PlanView skipSavings(String token, Long id, CommitmentSavingsService.EntryRequest request) { return savings.skip(authentication.authenticate(token), id, request); }
    public WebCreditCardService.CardListResponse creditCards(String token) { return creditCards.list(authentication.authenticate(token)); }
    public WebCreditCardService.CardResponse createCreditCard(String token, WebCreditCardService.CardRequest request) { return creditCards.create(authentication.authenticate(token), request); }
    public WebCreditCardService.CardResponse updateCreditCard(String token, Long id, WebCreditCardService.CardRequest request) { return creditCards.update(authentication.authenticate(token), id, request); }

    public WebLoanService.LoanResponse createLoan(String token, WebLoanService.LoanCreateRequest request) {
        return loans.create(authentication.authenticate(token), request);
    }

    public WebLoanService.LoanListResponse loans(String token) {
        return loans.list(authentication.authenticate(token));
    }
    public WebLoanService.LoanHistoryResponse loanHistory(String token, Long id) { return loans.history(authentication.authenticate(token), id); }

    public WebLoanService.LoanResponse updateLoan(
            String token, Long id, WebLoanService.LoanUpdateRequest request) {
        return loans.update(authentication.authenticate(token), id, request);
    }
    public void deleteLoan(String token, Long id) {
        loans.delete(authentication.authenticate(token), id);
    }
    public WebLoanService.LoanResponse markLoanEmiPaid(String token, Long id, YearMonth month) {
        return loans.markPaid(authentication.authenticate(token), id, month);
    }
    public WebLoanService.LoanResponse skipLoanEmi(String token, Long id, YearMonth month, WebLoanService.SkipRequest request) { return loans.skip(authentication.authenticate(token), id, month, request); }
    public WebLoanService.LoanResponse preCloseLoan(String token, Long id, WebLoanService.PreCloseRequest request) { return loans.preClose(authentication.authenticate(token), id, request); }
    public WebLoanService.LoanResponse restructureLoan(String token, Long id, WebLoanService.RestructureRequest request) { return loans.restructure(authentication.authenticate(token), id, request); }

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

    public void deleteMutualFund(String token, Long id) {
        mutualFunds.delete(authentication.authenticate(token), id);
    }

    public WebMutualFundService.MutualFundResponse createMutualFundSip(String token, Long id,
                                                                         WebMutualFundService.SipPlanRequest request) {
        return mutualFunds.createSip(authentication.authenticate(token), id, request);
    }

    public WebMutualFundService.MutualFundResponse updateMutualFundPlan(String token, Long id, WebMutualFundService.PlanUpdateRequest request) {
        return mutualFunds.updatePlan(authentication.authenticate(token), id, request);
    }
    public WebMutualFundService.TransactionResponse skipMutualFundSip(String token, Long id, YearMonth month) {
        return mutualFunds.skipSip(authentication.authenticate(token), id, month);
    }

    public java.util.List<StockMarketDataAdapter.StockSearchResult> searchStocks(String token, String query) {
        authentication.authenticate(token);
        return stockMarketData.search(query);
    }

    public WebStockService.StockResponse createStock(String token, WebStockService.StockCreateRequest request) {
        return stocks.create(authentication.authenticate(token), request);
    }

    public WebStockService.StockListResponse stocks(String token) {
        return stocks.list(authentication.authenticate(token));
    }
    public WebStockService.StockDetailResponse stock(String token, Long id) { return stocks.detail(authentication.authenticate(token), id); }

    public void deleteStock(String token, Long id) {
        stocks.delete(authentication.authenticate(token), id);
    }
    public WebStockService.StockResponse createStockMonthlyPlan(String token, Long id, WebStockService.MonthlyPlanRequest request) { return stocks.createMonthlyPlan(authentication.authenticate(token), id, request); }
    public WebStockService.MonthlyPlanOccurrenceResponse confirmStockMonthlyPlan(String token, Long id, java.time.YearMonth month, WebStockService.MonthlyPlanConfirmation request) { return stocks.confirmMonthlyPlan(authentication.authenticate(token), id, month, request); }
    public WebStockService.MonthlyPlanOccurrenceResponse skipStockMonthlyPlan(String token, Long id, java.time.YearMonth month) { return stocks.skipMonthlyPlan(authentication.authenticate(token), id, month); }
    public WebStockService.StockDetailResponse addStockPurchase(String token, Long id, WebStockService.StockPurchaseRequest request) { return stocks.addPurchase(authentication.authenticate(token), id, request); }
    public WebStockService.StockDetailResponse updateStockTransaction(String token, Long id, Long transactionId, WebStockService.StockPurchaseRequest request) { return stocks.updateTransaction(authentication.authenticate(token), id, transactionId, request); }

    public WebMutualFundService.TransactionResponse addMutualFundLumpSum(String token, Long id,
                                                                         WebMutualFundService.LumpSumRequest request) {
        return mutualFunds.addLumpSum(authentication.authenticate(token), id, request);
    }

    public WebMutualFundService.TransactionResponse confirmMutualFundSip(String token, Long id, YearMonth month,
                                                                         WebMutualFundService.LumpSumRequest request) {
        return mutualFunds.confirmSip(authentication.authenticate(token), id, month, request);
    }

    public WebMutualFundService.TransactionResponse updateMutualFundSip(String token, Long id, YearMonth month,
                                                                         WebMutualFundService.LumpSumRequest request) {
        return mutualFunds.updateSip(authentication.authenticate(token), id, month, request);
    }

    public WebMutualFundService.TransactionResponse updateMutualFundTransaction(String token, Long id, Long transactionId,
                                                                                 WebMutualFundService.LumpSumRequest request) {
        return mutualFunds.updateTransaction(authentication.authenticate(token), id, transactionId, request);
    }

    public PendingActionContextService.ContextResponse createPendingActionContext(
            String token, PendingActionContextService.ContextRequest request) {
        return actionContexts.create(authentication.authenticate(token).getId(), request);
    }

    public WebIncomeService.OutlookResponse incomeOutlook(String token) {
        return income.outlook(authentication.authenticate(token));
    }

    public WebIncomeService.SalaryResponse saveIncomeProfile(String token, WebIncomeService.ProfileRequest request) {
        return income.saveProfile(authentication.authenticate(token), request);
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

    public record LoginLinkRequest(String phoneNumber) {
    }

    public record LoginLinkResponse(String message) {
    }

    public record MagicLinkRequest(String token) {
    }

    public record AuthResponse(boolean authenticated, Instant expiresAt) {
    }

    public record DemoModeRequest(boolean enabled) {
    }

    public record UserReferenceEntityTypesResponse(List<UserReferenceEntityType> entityTypes) {
    }
}
