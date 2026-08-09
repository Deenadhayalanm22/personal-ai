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
                .containsEntry("category", "Food").containsEntry("sourceAccount", "UPI");
        assertThat(events.get(1).fields()).containsEntry("amount", new java.math.BigDecimal("120"))
                .containsEntry("category", "Transport").containsEntry("sourceAccount", "UPI");
    }

    @org.junit.jupiter.api.Test
    void extractsExplicitSingleExpenseAndKeepsMissingPaymentMissing() {
        var event = router.events("I spent 500 on groceries").getFirst();

        assertThat(event.fields()).containsEntry("amount", new java.math.BigDecimal("500"))
                .containsEntry("category", "Groceries")
                .doesNotContainKey("sourceAccount");
    }

    @org.junit.jupiter.api.Test
    void extractsExplicitTodayAndSourcePhrase() {
        var event = router.events("I spent 50 on groceries today from my upi").getFirst();

        assertThat(event.fields()).containsEntry("amount", new java.math.BigDecimal("50"))
                .containsEntry("category", "Groceries")
                .containsEntry("sourceAccount", "my upi");
    }

    @org.junit.jupiter.api.Test
    void extractsMonthlyBudgetWithoutTheModel() {
        var event = router.events("Set my monthly groceries budget to ₹10k").getFirst();

        assertThat(event.eventType()).isEqualTo("BUDGET_SET");
        assertThat(event.fields()).containsEntry("category", "Groceries")
                .containsEntry("amount", new java.math.BigDecimal("10000"));
    }

    @org.junit.jupiter.api.Test
    void extractsSingleExpenseWithPaymentAndPaidForGrammar() {
        var food = router.events("I spent 58 on curd and some ice cream through UPI").getFirst();
        assertThat(food.fields()).containsEntry("amount", new java.math.BigDecimal("58"))
                .containsEntry("category", "Food").containsEntry("sourceAccount", "UPI");

        var utility = router.events("Paid BESCOM electricity bill of 1850 using UPI").getFirst();
        assertThat(utility.fields()).containsEntry("amount", new java.math.BigDecimal("1850"))
                .containsEntry("category", "Utilities").containsEntry("sourceAccount", "UPI");
    }

    @org.junit.jupiter.api.Test
    void extractsTamilAndTanglishScenarioGrammarDeterministically() {
        var tamil = router.events("இன்று மளிகை பொருட்களுக்கு 230 ரூபாய் UPI மூலம் செலவு செய்தேன்").getFirst();
        assertThat(tamil.fields()).containsEntry("amount", new java.math.BigDecimal("230"))
                .containsEntry("category", "Groceries").containsEntry("sourceAccount", "UPI");

        var tamilMissingPayment = router.events("நேற்று மின்சார கட்டணத்திற்கு 650 ரூபாய் செலவு செய்தேன்").getFirst();
        assertThat(tamilMissingPayment.fields()).containsEntry("amount", new java.math.BigDecimal("650"))
                .containsEntry("category", "Utilities").doesNotContainKey("sourceAccount");

        var tanglish = router.events("Inniku bike petrol ku 350 rupees UPI la spend pannen").getFirst();
        assertThat(tanglish.fields()).containsEntry("amount", new java.math.BigDecimal("350"))
                .containsEntry("category", "Transport").containsEntry("sourceAccount", "UPI");

        var missingAmount = router.events("Nethu office lunch ku spend pannen").getFirst();
        assertThat(missingAmount.fields()).containsEntry("category", "Food").doesNotContainKey("amount");
    }
}
