package com.apps.deen_sa.finance.extension;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceDeterministicEventRouterTest {
    private final FinanceDeterministicEventRouter router = new FinanceDeterministicEventRouter();

    @ParameterizedTest
    @ValueSource(strings = {"I spent 260", "spent ₹260", "Paid Rs. 1,850", "I spent 10k."})
    void routesUnambiguousSparseExpensesWithoutTheModel(String text) {
        assertThat(router.eventType(text)).contains("EXPENSE");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Create my HDFC salary bank account with a current balance of 20000",
            "Create my ICICI Amazon Pay credit card with limit 150000, outstanding 0 and due day 12"
    })
    void routesExplicitAccountSetupWithoutTheModel(String text) {
        assertThat(router.eventType(text)).contains("ACCOUNT_SETUP");
        assertThat(router.query(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What did I spend today?",
            "Spent 80 on tea and 120 on auto using UPI",
            "Paid BESCOM electricity bill of 1850 using UPI"
    })
    void leavesQueriesEnrichedAndMultiEventSentencesToInterpretation(String text) {
        assertThat(router.eventType(text)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void extractsTwoExplicitExpensesWithoutDependingOnModelOutput() {
        var events = router.events("Spent 80 on tea and 120 on auto using UPI");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).fields()).containsEntry("amount", new java.math.BigDecimal("80"))
                .containsEntry("merchantName", "tea").containsEntry("sourceAccount", "UPI").doesNotContainKey("category");
        assertThat(events.get(1).fields()).containsEntry("amount", new java.math.BigDecimal("120"))
                .containsEntry("merchantName", "auto").containsEntry("sourceAccount", "UPI").doesNotContainKey("category");
    }

    @org.junit.jupiter.api.Test
    void extractsExplicitSingleExpenseAndKeepsMissingPaymentMissing() {
        var event = router.events("I spent 500 on groceries").getFirst();

        assertThat(event.fields()).containsEntry("amount", new java.math.BigDecimal("500"))
                .containsEntry("merchantName", "groceries").doesNotContainKey("category")
                .doesNotContainKey("sourceAccount");
    }

    @org.junit.jupiter.api.Test
    void extractsExplicitTodayAndSourcePhrase() {
        var event = router.events("I spent 50 on groceries today from my upi").getFirst();

        assertThat(event.fields()).containsEntry("amount", new java.math.BigDecimal("50"))
                .doesNotContainKey("category")
                .containsEntry("sourceAccount", "my upi");
    }

    @org.junit.jupiter.api.Test
    void extractsMonthlyBudgetWithoutTheModel() {
        var event = router.events("Set my monthly groceries budget to ₹10k").getFirst();

        assertThat(event.eventType()).isEqualTo("BUDGET_SET");
        assertThat(event.fields()).containsEntry("category", "groceries")
                .containsEntry("amount", new java.math.BigDecimal("10000"));
    }

    @org.junit.jupiter.api.Test
    void extractsSetupStyleSubcategoryBudgetWithoutTheModel() {
        var event = router.events("Setup my eating out budget ₹5,000 for this month.").getFirst();

        assertThat(event.eventType()).isEqualTo("BUDGET_SET");
        assertThat(event.fields()).containsEntry("category", "eating out")
                .containsEntry("amount", new java.math.BigDecimal("5000"));
    }

    @org.junit.jupiter.api.Test
    void extractsNaturalMonthlyScopeBalanceAsBudgetMutation() {
        var event = router.events("my grocery balance for this month is only 2000.").getFirst();

        assertThat(event.eventType()).isEqualTo("BUDGET_SET");
        assertThat(event.fields()).containsEntry("category", "grocery")
                .containsEntry("amount", new java.math.BigDecimal("2000"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "how much my grocery budget for this month",
            "how am i doing my grocery budget against this month planned"
    })
    void routesBudgetStatusQuestionsWithoutTheModel(String text) {
        assertThat(router.query(text)).contains("CURRENT_STATUS");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "what is my current balance in my bank",
            "what is my curent balance",
            "What is my balance?",
            "show my bank balance",
            "how much balance in my account"
    })
    void routesAccountBalanceQuestionsWithoutTheModel(String text) {
        assertThat(router.query(text)).contains("ACCOUNT_BALANCE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Setup my hdfc bank account where i have 40243 current balance",
            "Set up an HDFC account with an opening balance of 40243",
            "Add my salary account; its available balance is 40243",
            "Open a bank account for me with 40243 as the current balance"
    })
    void neverRoutesFreeFormAccountMutationsAsBalanceQueries(String text) {
        assertThat(router.query(text)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void leavesVegetableMeaningForTaxonomyBackedSemanticResolution() {
        var event = router.events("I spent 1300 on the buying vegitables using my upi").getFirst();

        assertThat(event.fields()).containsEntry("merchantName", "the buying vegitables").doesNotContainKey("category");
    }

    @org.junit.jupiter.api.Test
    void extractsSingleExpenseWithPaymentAndPaidForGrammar() {
        var food = router.events("I spent 58 on curd and some ice cream through UPI").getFirst();
        assertThat(food.fields()).containsEntry("amount", new java.math.BigDecimal("58"))
                .containsEntry("sourceAccount", "UPI").doesNotContainKey("category");

        var utility = router.events("Paid BESCOM electricity bill of 1850 using UPI").getFirst();
        assertThat(utility.fields()).containsEntry("amount", new java.math.BigDecimal("1850"))
                .containsEntry("sourceAccount", "UPI").doesNotContainKey("category");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Weekend dinner with family at BBQ Nation for ₹3,400 paid via HDFC Credit Card.",
            "Lunch at Pizza Hut for ₹1,200 paid via HDFC Credit Card.",
            "Booked movie tickets on BookMyShow for ₹900 using HDFC Credit Card."
    })
    void extractsDescriptionFirstExpensesWithoutModelRouting(String text) {
        var events = router.events(text);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("EXPENSE");
            assertThat(event.fields())
                    .containsKey("amount")
                    .containsEntry("sourceAccount", "HDFC Credit Card")
                    .doesNotContainKey("category");
        });
    }

    @org.junit.jupiter.api.Test
    void extractsBbqNationExpenseFactsExactly() {
        var event = router.events(
                "Weekend dinner with family at BBQ Nation for ₹3,400 paid via HDFC Credit Card.").getFirst();

        assertThat(event.fields())
                .containsEntry("amount", new java.math.BigDecimal("3400"))
                .containsEntry("merchantName", "Weekend dinner with family at BBQ Nation")
                .containsEntry("sourceAccount", "HDFC Credit Card");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Yearly term insurance premium of ₹50,000 paid using HDFC Credit Card.",
            "Health insurance premium of ₹12,500 paid via ICICI Credit Card."
    })
    void routesPremiumPaidByCardAsExpenseNotLiabilityPayment(String text) {
        var event = router.events(text).getFirst();

        assertThat(event.eventType()).isEqualTo("EXPENSE");
        assertThat(event.fields()).containsKeys("amount", "merchantName", "sourceAccount")
                .doesNotContainKey("category");
    }

    @org.junit.jupiter.api.Test
    void extractsInsurancePremiumFactsExactly() {
        var event = router.events(
                "Yearly term insurance premium of ₹50,000 paid using HDFC Credit Card.").getFirst();

        assertThat(event.fields())
                .containsEntry("amount", new java.math.BigDecimal("50000"))
                .containsEntry("merchantName", "Yearly term insurance premium")
                .containsEntry("sourceAccount", "HDFC Credit Card");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "On 6 July 2026 I purchased flight tickets for 6000 using my HDFC Millennia credit card",
            "On 16 July 2026 I bought medicines for 2500 using my ICICI Amazon Pay credit card"
    })
    void routesDatedOrdinaryPurchasesAsExpenses(String text) {
        var event = router.events(text).getFirst();

        assertThat(event.eventType()).isEqualTo("EXPENSE");
        assertThat(event.fields()).containsKeys("amount", "merchantName", "sourceAccount", "transactionDate")
                .doesNotContainKey("category");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "My June salary of 80000 was credited to my HDFC salary account on 1 June 2026",
            "My July salary of 80000 was credited to my HDFC salary account on 1 July 2026"
    })
    void routesExplicitDatedSalaryCreditsWithoutTheModel(String text) {
        var event = router.events(text).getFirst();

        assertThat(event.eventType()).isEqualTo("INCOME");
        assertThat(event.fields())
                .containsEntry("amount", new java.math.BigDecimal("80000"))
                .containsEntry("destinationAccount", "HDFC salary account")
                .containsEntry("subcategory", "Salary")
                .containsKey("transactionDate");
    }

    @org.junit.jupiter.api.Test
    void extractsTamilAndTanglishScenarioGrammarDeterministically() {
        var tamil = router.events("இன்று மளிகை பொருட்களுக்கு 230 ரூபாய் UPI மூலம் செலவு செய்தேன்").getFirst();
        assertThat(tamil.fields()).containsEntry("amount", new java.math.BigDecimal("230"))
                .containsEntry("sourceAccount", "UPI").doesNotContainKey("category");

        var tamilMissingPayment = router.events("நேற்று மின்சார கட்டணத்திற்கு 650 ரூபாய் செலவு செய்தேன்").getFirst();
        assertThat(tamilMissingPayment.fields()).containsEntry("amount", new java.math.BigDecimal("650"))
                .doesNotContainKey("category").doesNotContainKey("sourceAccount");

        var tanglish = router.events("Inniku bike petrol ku 350 rupees UPI la spend pannen").getFirst();
        assertThat(tanglish.fields()).containsEntry("amount", new java.math.BigDecimal("350"))
                .containsEntry("sourceAccount", "UPI").doesNotContainKey("category");

        var missingAmount = router.events("Nethu office lunch ku spend pannen").getFirst();
        assertThat(missingAmount.fields()).containsEntry("merchantName", "office lunch")
                .doesNotContainKey("category").doesNotContainKey("amount");
    }
}
