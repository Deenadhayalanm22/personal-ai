package com.apps.deen_sa.controller;

import com.apps.deen_sa.service.PendingActionContextService;
import com.apps.deen_sa.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/web")
public class WebFinanceController {
    static final String SESSION_COOKIE = "WEB_SESSION";
    private final WebManager webManager;
    private final boolean secureCookies;
    private final String cookieSameSite;

    public WebFinanceController(
            WebManager webManager,
            @Value("${app.web.secure-cookies:false}") boolean secureCookies,
            @Value("${app.web.cookie-same-site:Lax}") String cookieSameSite) {
        this.webManager = webManager;
        this.secureCookies = secureCookies;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/auth/login-link")
    public ResponseEntity<WebManager.LoginLinkResponse> requestLoginLink(
            @RequestBody WebManager.LoginLinkRequest request, HttpServletRequest httpRequest) {
        webManager.requestLoginLink(request.phoneNumber(), httpRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(new WebManager.LoginLinkResponse(
                "If this number is registered, we sent a login link to its WhatsApp account."));
    }

    @PostMapping("/auth/magic-link")
    public ResponseEntity<WebManager.AuthResponse> exchange(
            @RequestBody WebManager.MagicLinkRequest request, HttpServletResponse response) {
        WebAuthenticationService.SessionGrant grant = webManager.exchange(request.token());
        response.addHeader(HttpHeaders.SET_COOKIE,
                sessionCookie(grant.token(), Duration.between(Instant.now(), grant.expiresAt())).toString());
        return ResponseEntity.ok(new WebManager.AuthResponse(true, grant.expiresAt()));
    }

    @GetMapping("/auth/session")
    public WebManager.AuthResponse session(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        webManager.validateSession(token);
        return new WebManager.AuthResponse(true, null);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                       HttpServletResponse response) {
        webManager.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    /**
     * Returns only mode state; real and demo profile identifiers are never sent to the browser.
     */
    @GetMapping("/auth/demo-profile")
    public WebAuthenticationService.DemoProfile demoProfile(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.demoProfile(token);
    }

    /**
     * The frontend should call this once when its demo switch changes. All existing
     * API endpoints then use the selected profile through the session automatically.
     */
    @PutMapping("/auth/demo-profile")
    public WebAuthenticationService.DemoProfile setDemoProfile(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebManager.DemoModeRequest request) {
        return webManager.setDemoMode(token, request.enabled());
    }

    @GetMapping("/expense-taxonomy")
    public WebExpenseTaxonomyService.TaxonomyResponse expenseTaxonomy(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.expenseTaxonomy(token);
    }

    @GetMapping("/reference-entity-types")
    public WebManager.UserReferenceEntityTypesResponse referenceEntityTypes(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.referenceEntityTypes(token);
    }

    @PostMapping("/reference-preferences")
    @ResponseStatus(HttpStatus.CREATED)
    public WebUserReferencePreferenceService.UserReferencePreferenceResponse createReferencePreference(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebUserReferencePreferenceService.UserReferencePreferenceRequest request) {
        return webManager.createReferencePreference(token, request);
    }

    @GetMapping("/reference-preferences")
    public WebUserReferencePreferenceService.UserReferencePreferenceListResponse referencePreferences(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.referencePreferences(token);
    }

    @PostMapping("/reference-preferences/merge")
    public WebReferenceMergeService.MergeResponse mergeReferences(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebReferenceMergeService.MergeRequest request) {
        return webManager.mergeReferences(token, request);
    }

    @GetMapping("/expenses/monthly")
    public MoneyStoriesService.MonthlyStoriesApiResponse monthlyExpenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        return webManager.monthlyExpenses(token, month);
    }

    @GetMapping("/expenses")
    public FinancialTransactionListService.ExpensePage expenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory) {
        return webManager.expenses(token, month, date, limit, beforeId, category, subcategory);
    }

    @GetMapping("/expenses/calendar")
    public FinancialTransactionCalendarService.CalendarResponse expenseCalendar(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam String month) {
        return webManager.expenseCalendar(token, month);
    }

    @GetMapping("/expenses/options")
    public ExpenseEditOptionsService.ExpenseEditOptions expenseEditOptions(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.expenseEditOptions(token);
    }

    @PatchMapping("/expenses/{id}")
    public FinancialTransactionListService.ExpenseItem editExpense(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id, @RequestBody FinancialTransactionEditService.ExpenseUpdate request) {
        return webManager.editExpense(token, id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                              @PathVariable Long id) {
        webManager.deleteExpense(token, id);
    }

    @GetMapping("/recurring-commitments")
    public WebRecurringCommitmentService.CommitmentListResponse commitments(@CookieValue(name = SESSION_COOKIE, required = false) String token) { return webManager.commitments(token); }
    @PostMapping("/recurring-commitments") @ResponseStatus(HttpStatus.CREATED)
    public WebRecurringCommitmentService.CommitmentResponse createCommitment(@CookieValue(name = SESSION_COOKIE, required = false) String token, @RequestBody WebRecurringCommitmentService.CommitmentRequest request) { return webManager.createCommitment(token, request); }
    @PatchMapping("/recurring-commitments/{id}")
    public WebRecurringCommitmentService.CommitmentResponse updateCommitment(@CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id, @RequestBody WebRecurringCommitmentService.CommitmentRequest request) { return webManager.updateCommitment(token, id, request); }
    @GetMapping("/recurring-commitments/review")
    public WebRecurringCommitmentService.CommitmentReviewResponse commitmentReview(@CookieValue(name = SESSION_COOKIE, required = false) String token, @RequestParam(required = false) YearMonth month) { return webManager.commitmentReview(token, month); }
    @PostMapping("/recurring-commitments/review/{transactionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveCommitmentReview(@CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long transactionId, @RequestBody WebRecurringCommitmentService.ResolveRequest request) { webManager.resolveCommitmentReview(token, transactionId, request); }

    @GetMapping("/credit-cards")
    public WebCreditCardService.CardListResponse creditCards(@CookieValue(name = SESSION_COOKIE, required = false) String token) { return webManager.creditCards(token); }
    @PostMapping("/credit-cards") @ResponseStatus(HttpStatus.CREATED)
    public WebCreditCardService.CardResponse createCreditCard(@CookieValue(name = SESSION_COOKIE, required = false) String token, @RequestBody WebCreditCardService.CardRequest request) { return webManager.createCreditCard(token, request); }
    @PatchMapping("/credit-cards/{id}")
    public WebCreditCardService.CardResponse updateCreditCard(@CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id, @RequestBody WebCreditCardService.CardRequest request) { return webManager.updateCreditCard(token, id, request); }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    public WebLoanService.LoanResponse createLoan(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebLoanService.LoanCreateRequest request) {
        return webManager.createLoan(token, request);
    }

    @GetMapping("/loans")
    public WebLoanService.LoanListResponse loans(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.loans(token);
    }

    @PatchMapping("/loans/{id}")
    public WebLoanService.LoanResponse updateLoan(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id,
            @RequestBody WebLoanService.LoanUpdateRequest request) {
        return webManager.updateLoan(token, id, request);
    }

    @DeleteMapping("/loans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLoan(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id) {
        webManager.deleteLoan(token, id);
    }

    @PostMapping("/loans/{id}/emi-occurrences/{month}/paid")
    public WebLoanService.LoanResponse markLoanEmiPaid(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id,
            @PathVariable YearMonth month) {
        return webManager.markLoanEmiPaid(token, id, month);
    }

    @GetMapping("/income-outlook")
    public WebIncomeService.OutlookResponse incomeOutlook(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.incomeOutlook(token);
    }

    @PutMapping("/income-outlook/salary")
    public WebIncomeService.SalaryResponse saveIncomeProfile(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                                                              @RequestBody WebIncomeService.ProfileRequest request) {
        return webManager.saveIncomeProfile(token, request);
    }

    @GetMapping("/actions")
    public ActionManagementService.ActionListResponse actions(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.actions(token);
    }

    @PostMapping("/actions/{id}/complete")
    public ActionManagementService.ActionResponse completeAction(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id) {
        return webManager.completeAction(token, id);
    }

    @PostMapping("/mutual-funds")
    @ResponseStatus(HttpStatus.CREATED)
    public WebMutualFundService.MutualFundResponse createMutualFund(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebMutualFundService.MutualFundCreateRequest request) {
        return webManager.createMutualFund(token, request);
    }

    @GetMapping("/mutual-funds/search")
    public java.util.List<MfApiService.SchemeSearchResult> searchMutualFunds(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @RequestParam("q") String query) {
        return webManager.searchMutualFunds(token, query);
    }

    @GetMapping("/mutual-funds")
    public WebMutualFundService.MutualFundListResponse mutualFunds(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.mutualFunds(token);
    }

    @GetMapping("/mutual-funds/{id}")
    public WebMutualFundService.MutualFundDetailResponse mutualFund(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id) {
        return webManager.mutualFund(token, id);
    }

    @DeleteMapping("/mutual-funds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMutualFund(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id) {
        webManager.deleteMutualFund(token, id);
    }

    @PostMapping("/mutual-funds/{id}/sip")
    public WebMutualFundService.MutualFundResponse createMutualFundSip(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id,
            @RequestBody WebMutualFundService.SipPlanRequest request) {
        return webManager.createMutualFundSip(token, id, request);
    }

    @GetMapping("/stocks/search")
    public java.util.List<StockMarketDataAdapter.StockSearchResult> searchStocks(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @RequestParam("q") String query) {
        return webManager.searchStocks(token, query);
    }

    @PostMapping("/stocks")
    @ResponseStatus(HttpStatus.CREATED)
    public WebStockService.StockResponse createStock(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebStockService.StockCreateRequest request) {
        return webManager.createStock(token, request);
    }

    @GetMapping("/stocks")
    public WebStockService.StockListResponse stocks(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return webManager.stocks(token);
    }

    @GetMapping("/stocks/{id}")
    public WebStockService.StockDetailResponse stock(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id) {
        return webManager.stock(token, id);
    }

    @DeleteMapping("/stocks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStock(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id) {
        webManager.deleteStock(token, id);
    }

    @PostMapping("/stocks/{id}/monthly-plan")
    public WebStockService.StockResponse createStockMonthlyPlan(@CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id, @RequestBody WebStockService.MonthlyPlanRequest request) { return webManager.createStockMonthlyPlan(token, id, request); }

    @PostMapping("/stocks/{id}/monthly-plan-occurrences/{month}/confirm")
    public WebStockService.MonthlyPlanOccurrenceResponse confirmStockMonthlyPlan(@CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id, @PathVariable YearMonth month, @RequestBody WebStockService.MonthlyPlanConfirmation request) { return webManager.confirmStockMonthlyPlan(token, id, month, request); }

    @PostMapping("/mutual-funds/{id}/lump-sums")
    @ResponseStatus(HttpStatus.CREATED)
    public WebMutualFundService.TransactionResponse addMutualFundLumpSum(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id,
            @RequestBody WebMutualFundService.LumpSumRequest request) {
        return webManager.addMutualFundLumpSum(token, id, request);
    }

    @PostMapping("/mutual-funds/{id}/sip-occurrences/{month}/confirm")
    public WebMutualFundService.TransactionResponse confirmMutualFundSip(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id,
            @PathVariable YearMonth month, @RequestBody WebMutualFundService.LumpSumRequest request) {
        return webManager.confirmMutualFundSip(token, id, month, request);
    }

    @PatchMapping("/mutual-funds/{id}/sip-occurrences/{month}")
    public WebMutualFundService.TransactionResponse updateMutualFundSip(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id,
            @PathVariable YearMonth month, @RequestBody WebMutualFundService.LumpSumRequest request) {
        return webManager.updateMutualFundSip(token, id, month, request);
    }

    @PatchMapping("/mutual-funds/{id}/transactions/{transactionId}")
    public WebMutualFundService.TransactionResponse updateMutualFundTransaction(
            @CookieValue(name = SESSION_COOKIE, required = false) String token, @PathVariable Long id,
            @PathVariable Long transactionId, @RequestBody WebMutualFundService.LumpSumRequest request) {
        return webManager.updateMutualFundTransaction(token, id, transactionId, request);
    }

    @PostMapping("/expenses/calendar/context")
    @ResponseStatus(HttpStatus.CREATED)
    public PendingActionContextService.ContextResponse createPendingActionContext(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody PendingActionContextService.ContextRequest request) {
        return webManager.createPendingActionContext(token, request);
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite)
                .path("/api/web").maxAge(maxAge).build();
    }
}
