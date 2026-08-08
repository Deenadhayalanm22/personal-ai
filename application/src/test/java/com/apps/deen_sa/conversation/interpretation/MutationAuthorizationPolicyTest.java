package com.apps.deen_sa.conversation.interpretation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MutationAuthorizationPolicyTest {
    private final MutationAuthorizationPolicy policy = new MutationAuthorizationPolicy();

    @Test
    void authorizesTamilExpenseWhenAmountIsGroundedInCurrentMessage() {
        TurnInterpretation expense = expenseWithAmountEvidence("58");

        assertThat(policy.isAuthorized(expense, "தின்பண்டத்திற்கு 58 ரூபாய் செலவு செய்தேன்")).isTrue();
    }

    @Test
    void rejectsHistoryAmountCopiedIntoTamilQuestion() {
        TurnInterpretation leakedExpense = expenseWithAmountEvidence("58");

        assertThat(policy.isAuthorized(leakedExpense, "இன்று நான் எவ்வளவு செலவு செய்தேன்?")).isFalse();
    }

    @Test
    void authorizesExplicitAccountSetupWithoutTreatingOpeningBalanceAsTransactionAmount() {
        EventPatch account = new EventPatch(null, "ACCOUNT_SETUP", Map.of(
                "amount", 20000,
                "rawText", "Create my HDFC salary bank account with a current balance of 20000"
        ), List.of(), List.of(), List.of());
        TurnInterpretation setup = new TurnInterpretation(TurnType.NEW_EVENT, "ACCOUNT_SETUP", "en-IN", null,
                List.of(account), null, QueryPeriod.NONE, List.of(), 0.99);

        assertThat(policy.isAuthorized(setup,
                "Create my HDFC salary bank account with a current balance of 20000")).isTrue();
    }

    private TurnInterpretation expenseWithAmountEvidence(String evidence) {
        EventPatch event = new EventPatch(null, "EXPENSE", Map.of("amount", 58), List.of(), List.of(),
                List.of(new FieldEvidence("amount", "58", evidence, 0.99)));
        return new TurnInterpretation(TurnType.NEW_EVENT, "EXPENSE", "ta-IN", null,
                List.of(event), null, QueryPeriod.NONE, List.of(), 0.99);
    }
}
