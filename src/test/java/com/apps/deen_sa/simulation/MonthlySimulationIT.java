package com.apps.deen_sa.simulation;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.finance.account.AccountSetupHandler;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import com.apps.deen_sa.finance.payment.LiabilityPaymentHandler;
import com.apps.deen_sa.finance.account.ValueContainerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.core.mutation.StateMutationRepository;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.assertions.FinancialAssertions;

@Import(LLMTestConfiguration.class)
public class MonthlySimulationIT extends IntegrationTestBase {

    @Autowired
    AccountSetupHandler accountSetupHandler;

    @Autowired
    ExpenseHandler expenseHandler;

    @Autowired
    LiabilityPaymentHandler liabilityPaymentHandler;

    @Autowired
    ValueContainerService valueContainerService;

    @Autowired
    StateChangeRepository transactionRepository;

    @Autowired
    StateMutationRepository valueAdjustmentRepository;

    @Autowired
    StateContainerRepository valueContainerRepo;

    @Test
    void runSimpleMonthlySimulation() {
        FinancialSimulationContext ctx = new FinancialSimulationContext(
                accountSetupHandler,
                expenseHandler,
                liabilityPaymentHandler,
                valueContainerService
        );

        // start simulation at first of current month
        ctx.setCurrentDate(LocalDate.now().withDayOfMonth(1));

        FinancialSimulationRunner.simulate(ctx)
                .day(1).setupContainer("BANK_ACCOUNT", "My Bank", 100000)
                .day(2).setupContainer("CREDIT_CARD", "My Card", 0)
                .day(3).expense("Groceries", 1200, "CREDIT_CARD")
                .day(5).expense("Fuel", 2000, "CREDIT_CARD")
                .day(15).payCreditCard(3000, "My Card")
                .run();

        // --- Assertions: capture invariants ---
        FinancialAssertions.assertNoOrphanAdjustments(valueAdjustmentRepository, transactionRepository);
        FinancialAssertions.assertAdjustmentsMatchTransactions(transactionRepository, valueAdjustmentRepository);
        FinancialAssertions.assertNoNegativeBalances(valueContainerRepo);
        FinancialAssertions.assertCapacityLimitsRespected(valueContainerRepo);
        FinancialAssertions.assertAllTransactionsHaveValidStatus(transactionRepository);

        // Capture opening balances at start of simulation: for this test we treat containers that exist now as 'opening' after setup
        Map<Long, BigDecimal> opening = valueContainerRepo.findAll().stream()
            .collect(Collectors.toMap(v -> v.getId(), v -> v.getCurrentValue() == null ? BigDecimal.ZERO : v.getCurrentValue()));

        // For each container assert balance integrity
        for (Long cid : opening.keySet()) {
            FinancialAssertions.assertContainerBalance(cid, opening.get(cid), valueAdjustmentRepository, valueContainerRepo);
        }

        // Total money conserved
        FinancialAssertions.assertTotalMoneyConserved(opening, valueContainerRepo);
    }

        @Test
        void rerunSimulationIsIdempotent() {
        FinancialSimulationContext ctx = new FinancialSimulationContext(
            accountSetupHandler,
            expenseHandler,
            liabilityPaymentHandler,
            valueContainerService
        );

        ctx.setCurrentDate(LocalDate.now().withDayOfMonth(1));

        // Run once
        FinancialSimulationRunner.simulate(ctx)
            .day(1).setupContainer("BANK_ACCOUNT", "My Bank", 100000)
            .day(2).setupContainer("CREDIT_CARD", "My Card", 0)
            .day(3).expense("Groceries", 1200, "CREDIT_CARD")
            .day(5).expense("Fuel", 2000, "CREDIT_CARD")
            .day(15).payCreditCard(3000, "My Card")
            .run();

        // Snapshot container balances and adjustment count
        Map<Long, BigDecimal> before = valueContainerRepo.findAll().stream()
            .collect(Collectors.toMap(v -> v.getId(), v -> v.getCurrentValue() == null ? BigDecimal.ZERO : v.getCurrentValue()));

        long adjCountBefore = valueAdjustmentRepository.count();

        // Run same simulation again
        FinancialSimulationRunner.simulate(ctx)
            .day(1).setupContainer("BANK_ACCOUNT", "My Bank", 100000)
            .day(2).setupContainer("CREDIT_CARD", "My Card", 0)
            .day(3).expense("Groceries", 1200, "CREDIT_CARD")
            .day(5).expense("Fuel", 2000, "CREDIT_CARD")
            .day(15).payCreditCard(3000, "My Card")
            .run();

        // Snapshot after rerun
        Map<Long, BigDecimal> after = valueContainerRepo.findAll().stream()
            .collect(Collectors.toMap(v -> v.getId(), v -> v.getCurrentValue() == null ? BigDecimal.ZERO : v.getCurrentValue()));

        long adjCountAfter = valueAdjustmentRepository.count();

        // Assert containers unchanged
        for (Long cid : before.keySet()) {
            assertEquals(0, before.get(cid).compareTo(after.get(cid)), "Container " + cid + " changed after rerun");
        }

        // Assert adjustments did not increase (regression guard)
        assertEquals(adjCountBefore, adjCountAfter, "Adjustment count changed after rerun");
        }
}
