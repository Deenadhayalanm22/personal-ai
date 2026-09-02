package com.apps.deen_sa.v2.controller;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.service.MonthlyFinancialTransactionService;
import com.apps.deen_sa.web.WebAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v2/web")
@RequiredArgsConstructor
public class WebFinanceV2Controller {
    private static final String SESSION_COOKIE = "WEB_SESSION";

    private final WebAuthenticationService authentication;
    private final MonthlyFinancialTransactionService monthlyTransactions;

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
}
