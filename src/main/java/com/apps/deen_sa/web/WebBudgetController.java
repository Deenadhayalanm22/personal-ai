package com.apps.deen_sa.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web/budgets")
public class WebBudgetController {
    private final WebAuthenticationService authentication;
    private final WebBudgetService budgets;

    public WebBudgetController(WebAuthenticationService authentication, WebBudgetService budgets) {
        this.authentication = authentication; this.budgets = budgets;
    }

    @GetMapping
    public List<WebBudgetService.BudgetItem> list(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token) {
        return budgets.list(authentication.authenticate(token).getId());
    }

    @PutMapping
    public WebBudgetService.BudgetItem save(
            @CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token,
            @RequestBody WebBudgetService.BudgetUpdate request) {
        return budgets.save(authentication.authenticate(token).getId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@CookieValue(name = WebFinanceController.SESSION_COOKIE, required = false) String token,
                           @PathVariable Long id) {
        budgets.deactivate(authentication.authenticate(token).getId(), id);
    }
}
