package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import com.apps.deen_sa.finance.budget.BudgetInsightService;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationService;
import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseConfirmationFlowTest {
    private final StateChangeRepository repository = mock(StateChangeRepository.class);
    private final StateContainerService containers = mock(StateContainerService.class);
    private final ExpenseCompletenessEvaluator completeness = mock(ExpenseCompletenessEvaluator.class);
    private final ExpenseInputNormalizer normalizer = mock(ExpenseInputNormalizer.class);
    private final BudgetInsightService budgets = mock(BudgetInsightService.class);
    private ExpenseHandler handler;
    private ConversationContext context;

    @BeforeEach
    void setUp() {
        handler = new ExpenseHandler(
                mock(ExpenseClassifier.class), repository, containers, completeness,
                mock(AdjustmentCommandFactory.class), mock(StateMutationService.class), normalizer,
                new ObjectMapper().findAndRegisterModules(), budgets);
        context = new ConversationContext();
        context.setUserId(7L);
        when(normalizer.normalize(any(ExpenseDto.class), anyString(), any(ConversationContext.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(completeness.evaluate(any(ExpenseDto.class))).thenReturn(CompletenessLevelEnum.OPERATIONAL);
        when(containers.getActiveContainers(7L)).thenReturn(List.of());
        when(budgets.alert(any(StateChangeEntity.class), anyString())).thenReturn(Optional.empty());
        when(repository.save(any(StateChangeEntity.class))).thenAnswer(call -> {
            StateChangeEntity entity = call.getArgument(0);
            if (entity.getId() == null) entity.setId(42L);
            return entity;
        });
    }

    @Test
    void previewsUnconfiguredSourceWithoutWritingThenDiscards() {
        SpeechResult preview = handler.handleInterpreted(expensePatch(), rawText(), context);

        assertThat(preview.getStatus()).isEqualTo(SpeechStatus.FOLLOWUP);
        assertThat(preview.getMessage())
                .contains("Amount: ₹3400")
                .contains("Category: Food & Dining")
                .contains("Subcategory: Eating Out")
                .contains("Source: null")
                .contains("Detected account: HDFC Credit Card (not configured)");
        assertThat(preview.getActions()).extracting("title").containsExactly("Confirm", "Discard");
        verify(repository, never()).save(any());

        SpeechResult discarded = handler.handleInterpretedFollowup(emptyPatch(), "DISCARD_EXPENSE", context);

        assertThat(discarded.getMessage()).isEqualTo("Discarded. Nothing was saved.");
        assertThat(context.isInFollowup()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void confirmationSavesThenOffersOptionalAccountSetup() {
        handler.handleInterpreted(expensePatch(), rawText(), context);

        SpeechResult confirmed = handler.handleInterpretedFollowup(emptyPatch(), "CONFIRM_EXPENSE", context);

        assertThat(confirmed.getStatus()).isEqualTo(SpeechStatus.FOLLOWUP);
        assertThat(confirmed.getMessage())
                .contains("Added ₹3400")
                .contains("HDFC Credit Card is not set up");
        assertThat(confirmed.getActions()).extracting("title")
                .containsExactly("Set up account", "Not now");
        assertThat(confirmed.getSavedEntity()).isInstanceOf(StateChangeEntity.class);
        verify(repository).save(any(StateChangeEntity.class));

        when(repository.findById(42L)).thenReturn(Optional.of((StateChangeEntity) confirmed.getSavedEntity()));
        SpeechResult skipped = handler.handleInterpretedFollowup(emptyPatch(), "SKIP_SOURCE_SETUP", context);
        assertThat(skipped.getStatus()).isEqualTo(SpeechStatus.SAVED);
        assertThat(context.isInFollowup()).isFalse();
        verify(containers, never()).createProvisional(any(), anyString(), anyString());
    }

    private EventPatch expensePatch() {
        return new EventPatch(null, "EXPENSE", Map.of(
                "amount", new BigDecimal("3400"),
                "category", "Food & Dining",
                "subcategory", "Eating Out",
                "sourceAccount", "HDFC Credit Card",
                "transactionDate", LocalDate.of(2026, 8, 12)
        ), List.of(), List.of(), List.of());
    }

    private EventPatch emptyPatch() {
        return new EventPatch(null, "EXPENSE", Map.of(), List.of(), List.of(), List.of());
    }

    private String rawText() {
        return "Weekend dinner with family at BBQ Nation for ₹3,400 paid via HDFC Credit Card.";
    }
}
