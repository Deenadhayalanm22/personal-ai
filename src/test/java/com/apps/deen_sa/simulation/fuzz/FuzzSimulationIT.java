package com.apps.deen_sa.simulation.fuzz;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.simulation.FinancialSimulationContext;
import com.apps.deen_sa.simulation.FinancialSimulationRunner;
import com.apps.deen_sa.simulation.LLMTestConfiguration;
import com.apps.deen_sa.core.value.ValueContainerEntity;
import com.apps.deen_sa.core.value.ValueContainerRepo;
import com.apps.deen_sa.core.value.ValueAdjustmentRepository;
import com.apps.deen_sa.core.transaction.TransactionRepository;
import com.apps.deen_sa.assertions.FinancialAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Import(LLMTestConfiguration.class)
public class FuzzSimulationIT extends IntegrationTestBase {

    @Autowired
    ValueContainerRepo valueContainerRepo;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    ValueAdjustmentRepository valueAdjustmentRepository;

    @Autowired
    com.apps.deen_sa.finance.account.AccountSetupHandler accountSetupHandler;

    @Autowired
    com.apps.deen_sa.finance.expense.ExpenseHandler expenseHandler;

    @Autowired
    com.apps.deen_sa.finance.payment.LiabilityPaymentHandler liabilityPaymentHandler;

    @Autowired
    com.apps.deen_sa.finance.account.ValueContainerService valueContainerService;

    @Test
    void runFuzzSimulations() {
        // Read fuzz iterations from system property, default to 50 for regular runs
        final String fuzzIterationsProperty = System.getProperty("fuzz.iterations", "50");
        final int runs = Integer.parseInt(fuzzIterationsProperty);
        final int actionsPerRun = 30;

        System.out.println("Running " + runs + " fuzz simulations with " + actionsPerRun + " actions each");

        for (int i = 0; i < runs; i++) {
            long seed = 1000L + i;

            try {
                runOne(seed, actionsPerRun);
                
                // Clean up data after each iteration to prevent database bloat
                cleanupTestData();
            } catch (AssertionError | Exception e) {
                System.err.println("====================================");
                System.err.println("FUZZ SIMULATION FAILURE");
                System.err.println("====================================");
                System.err.println("Seed: " + seed);
                System.err.println("To reproduce, run:");
                System.err.println("  mvn -B verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=" + seed);
                System.err.println("====================================");
                throw new AssertionError("Fuzz test failed with seed " + seed + ". See logs above for reproduction steps.", e);
            }
        }
    }

    private void runOne(long seed, int actions) {
        // fresh context and runner
        FinancialSimulationContext ctx = new FinancialSimulationContext(
            accountSetupHandler,
            expenseHandler,
            liabilityPaymentHandler,
            valueContainerService
        );

        ctx.setCurrentDate(LocalDate.now().withDayOfMonth(1));

        FinancialSimulationRunner runner = FinancialSimulationRunner.simulate(ctx);

        // initial setup containers
        runner.day(1).setupContainer("BANK_ACCOUNT", "Bank-"+seed, 100000)
                .day(1).setupContainer("CREDIT_CARD", "Card-"+seed, 0)
                .day(1).setupContainer("CASH", "Cash-"+seed, 10000)
                .run();

        // map container type to entity for generator
        Map<String, ValueContainerEntity> map = new HashMap<>();
        for (ValueContainerEntity v : valueContainerRepo.findAll()) {
            map.putIfAbsent(v.getContainerType(), v);
        }

        RandomFinancialScenarioGenerator gen = new RandomFinancialScenarioGenerator(seed, 28, map);
        List<RandomFinancialScenarioGenerator.ScenarioAction> scenario = gen.generate(actions);

        // snapshot opening balances
        Map<Long, BigDecimal> opening = valueContainerRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(v -> v.getId(), v -> v.getCurrentValue() == null ? BigDecimal.ZERO : v.getCurrentValue()));

        // execute scenario actions and record replay lines
        for (RandomFinancialScenarioGenerator.ScenarioAction a : scenario) {
            runner.day(a.day());
            switch (a.type()) {
                case EXPENSE:
                    runner.expense(a.description(), a.amount(), a.sourceAccount());
                    break;
                case PAY_CREDIT_CARD:
                    runner.payCreditCard(a.amount(), a.targetName() == null ? "Card-"+seed : a.targetName());
                    break;
            }
        }
        runner.run();

        // run invariants
        FinancialAssertions.assertNoOrphanAdjustments(valueAdjustmentRepository, transactionRepository);
        FinancialAssertions.assertAdjustmentsMatchTransactions(transactionRepository, valueAdjustmentRepository);
        FinancialAssertions.assertNoNegativeBalances(valueContainerRepo);
        FinancialAssertions.assertCapacityLimitsRespected(valueContainerRepo);
        FinancialAssertions.assertAllTransactionsHaveValidStatus(transactionRepository);

        // per-container balance integrity
        for (ValueContainerEntity v : valueContainerRepo.findAll()) {
            FinancialAssertions.assertContainerBalance(v.getId(), opening.getOrDefault(v.getId(), BigDecimal.ZERO), valueAdjustmentRepository, valueContainerRepo);
        }

        FinancialAssertions.assertTotalMoneyConserved(opening, valueContainerRepo);
    }

    @Test
    void testReproduceSeed() {
        // This test allows reproducing a specific failed fuzz scenario
        final String seedProperty = System.getProperty("fuzz.seed");
        if (seedProperty == null) {
            System.out.println("Skipping seed reproduction test - no fuzz.seed property set");
            return;
        }

        long seed = Long.parseLong(seedProperty);
        final int actionsPerRun = 30;

        System.out.println("Reproducing fuzz simulation with seed: " + seed);
        
        try {
            runOne(seed, actionsPerRun);
            System.out.println("Seed " + seed + " passed successfully");
        } catch (AssertionError | Exception e) {
            System.err.println("Seed " + seed + " failed to reproduce");
            throw e;
        }
    }

    /**
     * Clean up test data after each iteration to prevent database bloat
     * and potential connection issues during long-running fuzz tests.
     */
    private void cleanupTestData() {
        // Delete all test data in reverse order of dependencies
        valueAdjustmentRepository.deleteAll();
        transactionRepository.deleteAll();
        valueContainerRepo.deleteAll();
    }
    
}
