package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.conversation.context.PendingActionContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/web")
public class WebFinanceController {
    static final String SESSION_COOKIE = "WEB_SESSION";
    private final WebAuthenticationService authentication;
    private final WebLoginRequestService loginRequests;
    private final MonthlyExpenseService monthlyExpenses;
    private final ExpenseCalendarService expenseCalendar;
    private final PendingActionContextService actionContexts;
    private final WebExpenseService expenses;
    private final WebExpenseTaxonomyService taxonomy;
    private final WebTagService tags;
    private final boolean secureCookies;
    private final String cookieSameSite;

    public WebFinanceController(WebAuthenticationService authentication, WebLoginRequestService loginRequests,
            MonthlyExpenseService monthlyExpenses,
            ExpenseCalendarService expenseCalendar, PendingActionContextService actionContexts,
            WebExpenseService expenses,
            WebExpenseTaxonomyService taxonomy, WebTagService tags,
            @Value("${app.web.secure-cookies:false}") boolean secureCookies,
            @Value("${app.web.cookie-same-site:Lax}") String cookieSameSite) {
        this.authentication = authentication; this.loginRequests = loginRequests;
        this.monthlyExpenses = monthlyExpenses;
        this.expenseCalendar = expenseCalendar;
        this.actionContexts = actionContexts;
        this.expenses = expenses;
        this.taxonomy = taxonomy;
        this.tags = tags;
        this.secureCookies = secureCookies; this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/auth/login-link")
    public ResponseEntity<LoginLinkResponse> requestLoginLink(@RequestBody LoginLinkRequest request,
                                                               HttpServletRequest httpRequest) {
        loginRequests.request(request.phoneNumber(), clientAddress(httpRequest));
        return ResponseEntity.accepted().body(new LoginLinkResponse(
                "If this number is registered, we sent a login link to its WhatsApp account."));
    }

    @PostMapping("/auth/magic-link")
    public ResponseEntity<AuthResponse> exchange(@RequestBody MagicLinkRequest request,
                                                  HttpServletResponse response) {
        WebAuthenticationService.SessionGrant grant = authentication.exchange(request.token());
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, grant.token())
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite).path("/api/web")
                .maxAge(Duration.between(Instant.now(), grant.expiresAt())).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(new AuthResponse(true, grant.expiresAt()));
    }

    @GetMapping("/auth/session")
    public AuthResponse session(@CookieValue(name = SESSION_COOKIE, required = false) String token) {
        authentication.authenticate(token);
        return new AuthResponse(true, null);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                       HttpServletResponse response) {
        authentication.logout(token);
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true).secure(secureCookies).sameSite(cookieSameSite).path("/api/web")
                .maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @GetMapping("/expenses/monthly")
    public MonthlyExpenseService.MonthlyExpenseResponse monthlyExpenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = selectedMonth(user, month);
        return monthlyExpenses.summarize(user, selected);
    }

    @GetMapping("/expenses")
    public WebExpenseService.ExpensePage expenses(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) String tagIds,
            @RequestParam(defaultValue = "any") String tagMatch) {
        AppUserEntity user = authentication.authenticate(token);
        return expenses.list(user, selectedMonth(user, month), limit, beforeId,
                new WebExpenseService.ExpenseFilter(category, subcategory,
                        parseTagIds(tagIds), parseTagMatch(tagMatch), date));
    }

    @GetMapping("/expenses/calendar")
    public ExpenseCalendarService.CalendarResponse expenseCalendar(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestParam String month) {
        AppUserEntity user = authentication.authenticate(token);
        YearMonth selected = parseCalendarMonth(month);
        try {
            return expenseCalendar.calendar(user, selected);
        } catch (RuntimeException failure) {
            throw new WebApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CALENDAR_FETCH_FAILED",
                    "Unable to load the expense calendar.");
        }
    }

    @PostMapping("/expenses/calendar/context")
    @ResponseStatus(HttpStatus.CREATED)
    public PendingActionContextService.ContextResponse createPendingActionContext(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody PendingActionContextService.ContextRequest request) {
        AppUserEntity user = authentication.authenticate(token);
        try {
            return actionContexts.create(user.getId(), request);
        } catch (WebApiException validation) {
            throw validation;
        } catch (RuntimeException failure) {
            throw new WebApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTEXT_CREATE_FAILED",
                    "Unable to prepare WhatsApp recording.");
        }
    }

    @GetMapping("/expense-taxonomy")
    public WebExpenseTaxonomyService.TaxonomyResponse expenseTaxonomy(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        authentication.authenticate(token);
        return taxonomy.options();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public WebTagService.TagItem createTag(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @RequestBody WebTagService.CreateTagRequest request) {
        return tags.create(authentication.authenticate(token), request);
    }

    @GetMapping("/tags")
    public java.util.List<WebTagService.TagItem> tags(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return tags.list(authentication.authenticate(token));
    }

    @PatchMapping("/expenses/{id}/classification")
    public WebExpenseService.ExpenseItem editClassification(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id,
            @RequestBody WebExpenseService.ClassificationUpdate request) {
        return expenses.editClassification(authentication.authenticate(token), id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable Long id) {
        expenses.delete(authentication.authenticate(token), id);
    }

    private YearMonth selectedMonth(AppUserEntity user, YearMonth requested) {
        return requested == null ? YearMonth.now(ZoneId.of(user.getTimezone())) : requested;
    }

    private YearMonth parseCalendarMonth(String value) {
        if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])"))
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "month must use YYYY-MM format");
        try {
            return YearMonth.parse(value);
        } catch (DateTimeException invalid) {
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "month must use YYYY-MM format");
        }
    }

    private String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private List<Long> parseTagIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            List<Long> parsed = Arrays.stream(value.split(",", -1)).map(String::trim)
                    .map(Long::parseLong).toList();
            if (parsed.stream().anyMatch(id -> id < 1)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_TAG_IDS",
                    "tagIds must contain positive integer IDs");
        }
    }

    private WebExpenseService.TagMatch parseTagMatch(String value) {
        try {
            return WebExpenseService.TagMatch.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_TAG_MATCH",
                    "tagMatch must be either any or all");
        }
    }

    public record LoginLinkRequest(String phoneNumber) { }
    public record LoginLinkResponse(String message) { }
    public record MagicLinkRequest(String token) { }
    public record AuthResponse(boolean authenticated, Instant expiresAt) { }
}
