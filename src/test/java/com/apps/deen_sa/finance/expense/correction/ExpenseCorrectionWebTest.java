package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.mutation.*;
import com.apps.deen_sa.finance.legacy.state.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpenseCorrectionWebTest {
    private final StateChangeRepository transactions = mock(StateChangeRepository.class);
    private final StateMutationService mutations = mock(StateMutationService.class);
    private final StateContainerRepository containers = mock(StateContainerRepository.class);
    private final ExpenseCorrectionService service = new ExpenseCorrectionService(transactions,
            mock(StateMutationRepository.class), mutations, containers);

    @Test
    void classificationEditCreatesANewVersionAndSupersedesOriginal() {
        StateChangeEntity original = expense();
        when(transactions.findExpenseForUpdate(10L, "42")).thenReturn(Optional.of(original));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StateChangeEntity replacement = service.editClassification(42L, 10L, 2, "Food", "Groceries");

        assertThat(original.getRecordStatus()).isEqualTo(ExpenseRecordStatus.SUPERSEDED);
        assertThat(replacement.getRecordStatus()).isEqualTo(ExpenseRecordStatus.ACTIVE);
        assertThat(replacement.getCategory()).isEqualTo("Food");
        assertThat(replacement.getSubcategory()).isEqualTo("Groceries");
        assertThat(replacement.getRecordVersion()).isEqualTo(3);
        assertThat(replacement.getReplacesTransactionId()).isEqualTo(10L);
    }

    @Test
    void assigningFundedOwnedAccountAppliesImpactAndCompletesEnrichment() {
        StateChangeEntity original = expense();
        original.setNeedsEnrichment(true);
        original.setSubcategory("Other Shopping");
        StateContainerEntity card = new StateContainerEntity();
        card.setId(5L); card.setOwnerId(42L); card.setStatus("ACTIVE"); card.setContainerType("CREDIT_CARD");
        card.setCurrentValue(BigDecimal.ZERO);
        when(transactions.findExpenseForUpdate(10L, "42")).thenReturn(Optional.of(original));
        when(containers.findById(5L)).thenReturn(Optional.of(card));
        when(transactions.save(any())).thenAnswer(invocation -> {
            StateChangeEntity value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(11L);
            return value;
        });

        StateChangeEntity replacement = service.editDetails(42L, 10L, 2, null, null, 5L);

        assertThat(replacement.getSourceContainerId()).isEqualTo(5L);
        assertThat(replacement.isFinanciallyApplied()).isTrue();
        assertThat(replacement.isNeedsEnrichment()).isFalse();
        assertThat(replacement.getCompletenessLevel()).isEqualTo(CompletenessLevelEnum.FINANCIAL);
        verify(mutations).apply(eq(card), argThat(command -> command.getReferenceTxId().equals(11L)
                && command.getAmount().compareTo(new BigDecimal("850")) == 0));
    }

    @Test
    void deleteVoidsRatherThanRemovingTheRow() {
        StateChangeEntity original = expense();
        when(transactions.findExpenseForUpdate(10L, "42")).thenReturn(Optional.of(original));

        service.voidExpense(42L, 10L, 2);

        assertThat(original.getRecordStatus()).isEqualTo(ExpenseRecordStatus.VOIDED);
        assertThat(original.getCorrectionReason()).isEqualTo("USER_DELETED_FROM_WEB");
        verify(transactions, never()).delete(any(StateChangeEntity.class));
    }

    @Test
    void discardsOnlyTransactionsThatStillNeedEnrichment() {
        StateChangeEntity original = expense();
        original.setNeedsEnrichment(true);
        when(transactions.findExpenseForUpdate(10L, "42")).thenReturn(Optional.of(original));

        service.voidEnrichmentExpense(42L, 10L, 2);

        assertThat(original.getRecordStatus()).isEqualTo(ExpenseRecordStatus.VOIDED);
        assertThat(original.getCorrectionReason()).isEqualTo("USER_DISCARDED_ENRICHMENT_FROM_WEB");
        verify(transactions, never()).delete(any(StateChangeEntity.class));
    }

    @Test
    void refusesToDiscardCompletedTransactionThroughEnrichmentApi() {
        StateChangeEntity original = expense();
        original.setNeedsEnrichment(false);
        when(transactions.findExpenseForUpdate(10L, "42")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.voidEnrichmentExpense(42L, 10L, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("That transaction is not awaiting enrichment.");
        assertThat(original.getRecordStatus()).isEqualTo(ExpenseRecordStatus.ACTIVE);
    }

    private StateChangeEntity expense() {
        StateChangeEntity value = new StateChangeEntity();
        value.setId(10L); value.setUserId("42"); value.setAmount(new BigDecimal("850"));
        value.setTimestamp(Instant.parse("2026-08-12T08:00:00Z")); value.setCategory("Other");
        value.setRecordStatus(ExpenseRecordStatus.ACTIVE); value.setRecordVersion(2);
        value.setTransactionType(StateChangeTypeEnum.EXPENSE); value.setFinanciallyApplied(false);
        return value;
    }
}
