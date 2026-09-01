package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PendingFieldScopeTest {

    @Test
    void expenseDetailsFollowupPreservesEveryExplicitFieldFromOneMessage() {
        EventPatch modelPatch = new EventPatch(null, "EXPENSE", Map.of(
                "category", "Shopping",
                "subcategory", "Clothing",
                "sourceAccount", "HDFC"
        ), List.of(), List.of(), List.of());

        EventPatch scoped = UnifiedConversationEngine.scopeToPendingField(
                modelPatch, ExpenseHandler.EXPENSE_DETAILS);

        assertThat(scoped.fields().asMap()).containsExactlyEntriesOf(modelPatch.fields().asMap());
    }

    @Test
    void categoryAnswerCannotAlsoInferPaymentSourceFromConversationHistory() {
        EventPatch modelPatch = new EventPatch(null, "EXPENSE", Map.of(
                "category", "Food",
                "sourceAccount", "BANK_ACCOUNT",
                "rawText", "Coffee and snacks outside"
        ), List.of(), List.of(), List.of(
                new FieldEvidence("category", "Food", "Coffee and snacks outside", 0.95),
                new FieldEvidence("sourceAccount", "BANK_ACCOUNT", "previously used UPI", 0.70)
        ));

        EventPatch scoped = UnifiedConversationEngine.scopeToPendingField(modelPatch, "category");

        assertThat(scoped.fields().asMap()).containsExactlyEntriesOf(Map.of("category", "Food"));
        assertThat(scoped.evidence()).extracting(FieldEvidence::field).containsExactly("category");
    }

    @Test
    void spentAtPendingFieldMapsToTransactionDateSchemaField() {
        EventPatch modelPatch = new EventPatch(null, "EXPENSE",
                Map.of("transactionDate", "2026-08-08", "category", "Food"),
                List.of(), List.of(), List.of(new FieldEvidence(
                "transactionDate", "2026-08-08", "today", 0.95)));

        EventPatch scoped = UnifiedConversationEngine.scopeToPendingField(modelPatch, "spentAt");

        assertThat(scoped.fields().asMap()).containsOnlyKeys("transactionDate");
    }

    @Test
    void pendingScopeRunsBeforeAuthorizationAndRemovesHistoricalAmount() {
        ConversationContext context = new ConversationContext();
        context.setActiveIntent("EXPENSE");
        context.setWaitingForField("category");
        EventPatch modelPatch = new EventPatch(null, "EXPENSE", Map.of(
                "amount", 260,
                "category", "Food",
                "sourceAccount", "BANK_ACCOUNT"
        ), List.of(), List.of(), List.of(
                new FieldEvidence("amount", "260", "I spent 260", 0.90),
                new FieldEvidence("category", "Food", "Coffee and snacks outside", 0.95)
        ));
        TurnInterpretation modelTurn = new TurnInterpretation(TurnType.ANSWER_TO_PENDING_EVENT, "EXPENSE", "en-IN", null,
                List.of(modelPatch), null, QueryPeriod.NONE, List.of(), 0.90);

        TurnInterpretation scoped = UnifiedConversationEngine.scopePendingTurn(modelTurn, context);

        assertThat(scoped.turnType()).isEqualTo(TurnType.ANSWER_TO_PENDING_EVENT);
        assertThat(scoped.events()).singleElement().satisfies(event ->
                assertThat(event.fields().asMap()).containsExactlyEntriesOf(Map.of("category", "Food")));
        assertThat(new MutationAuthorizationPolicy().isAuthorized(scoped, "Coffee and snacks outside")).isTrue();
    }

    @Test
    void explicitNewExpenseSupersedesPendingExpense() {
        ConversationContext context = new ConversationContext();
        context.setActiveIntent("EXPENSE");
        context.setWaitingForField("confirmExpense");
        context.setPartialObject(Map.of("amount", 300));
        EventPatch amazon = new EventPatch(null, "EXPENSE", Map.of("amount", 505, "merchantName", "Amazon grocery"),
                List.of(), List.of(), List.of(new FieldEvidence("amount", "505", "505", 1.0)));
        TurnInterpretation modelTurn = new TurnInterpretation(TurnType.NEW_EVENT, "EXPENSE", "en-IN", null,
                List.of(amazon), null, QueryPeriod.NONE, List.of(), 1.0);

        TurnInterpretation scoped = UnifiedConversationEngine.scopePendingTurn(modelTurn, context);

        assertThat(scoped).isSameAs(modelTurn);
        assertThat(context.isInFollowup()).isFalse();
        assertThat(scoped.events().getFirst().fields().asMap()).containsEntry("amount", 505);
    }

    @Test
    void exactUserReplyRecoversAFreeTextPendingFieldWhenModelReturnsNoEvent() {
        ConversationContext context = new ConversationContext();
        context.setActiveIntent("EXPENSE");
        context.setWaitingForField("category");
        TurnInterpretation emptyModelTurn = new TurnInterpretation(TurnType.AMBIGUOUS, null, "en-IN", null,
                List.of(), null, QueryPeriod.NONE, List.of("No event extracted"), 0.40);

        TurnInterpretation recovered = UnifiedConversationEngine.recoverPendingTextAnswer(
                emptyModelTurn, "Coffee and snacks outside", context, "string");

        assertThat(recovered.turnType()).isEqualTo(TurnType.ANSWER_TO_PENDING_EVENT);
        assertThat(recovered.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("EXPENSE");
            assertThat(event.fields().asMap())
                    .containsExactlyEntriesOf(Map.of("category", "Coffee and snacks outside"));
        });
    }

    @Test
    void doesNotConvertQueriesOrNumericAndDateFieldsIntoFreeTextAnswers() {
        ConversationContext context = new ConversationContext();
        context.setActiveIntent("EXPENSE");
        context.setWaitingForField("category");
        TurnInterpretation query = new TurnInterpretation(TurnType.QUERY, null, "en-IN", null,
                List.of(), null, QueryPeriod.TODAY, List.of(), 0.99);
        assertThat(UnifiedConversationEngine.recoverPendingTextAnswer(
                query, "What did I spend today?", context, "string")).isSameAs(query);

        context.setWaitingForField("amount");
        TurnInterpretation ambiguous = new TurnInterpretation(TurnType.AMBIGUOUS, null, "en-IN", null,
                List.of(), null, QueryPeriod.NONE, List.of(), 0.40);
        assertThat(UnifiedConversationEngine.recoverPendingTextAnswer(
                ambiguous, "many", context, "number")).isSameAs(ambiguous);

        context.setWaitingForField("transactionDate");
        assertThat(UnifiedConversationEngine.recoverPendingTextAnswer(
                ambiguous, "yesterday", context, "string")).isSameAs(ambiguous);
    }
}
