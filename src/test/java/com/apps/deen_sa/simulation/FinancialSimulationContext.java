package com.apps.deen_sa.simulation;

import com.apps.deen_sa.finance.account.StateContainerService;
import com.apps.deen_sa.finance.account.AccountSetupHandler;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import com.apps.deen_sa.finance.payment.LiabilityPaymentHandler;
import java.time.LocalDate;

/**
 * Test-only helper that holds simulated date and references to application handlers.
 * This class contains no assertions and is placed under src/test only.
 */
public class FinancialSimulationContext {

    private LocalDate currentDate;
    private Long userId = 1L;

    public final AccountSetupHandler accountSetupHandler;
    public final ExpenseHandler expenseHandler;
    public final LiabilityPaymentHandler liabilityPaymentHandler;
    public final StateContainerService stateContainerService;

    public FinancialSimulationContext(
            AccountSetupHandler accountSetupHandler,
            ExpenseHandler expenseHandler,
            LiabilityPaymentHandler liabilityPaymentHandler,
            StateContainerService stateContainerService
    ) {
        this.accountSetupHandler = accountSetupHandler;
        this.expenseHandler = expenseHandler;
        this.liabilityPaymentHandler = liabilityPaymentHandler;
        this.stateContainerService = stateContainerService;
        this.currentDate = LocalDate.now();
    }

    public LocalDate getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(LocalDate date) {
        this.currentDate = date;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void nextDay() {
        this.currentDate = this.currentDate.plusDays(1);
    }

    public void advanceDays(int n) {
        this.currentDate = this.currentDate.plusDays(n);
    }
}
