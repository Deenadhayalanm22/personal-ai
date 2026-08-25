package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.state.*;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import com.apps.deen_sa.finance.expense.ExpenseSourceAccountResolver;
import com.apps.deen_sa.finance.expense.draft.ExpenseDraftService;
import com.apps.deen_sa.finance.expense.draft.*;
import com.apps.deen_sa.dto.ExpenseDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardEnrichmentServiceTest {
    @Test
    void combinesDeferredTransactionsAndIncompleteAccounts() {
        StateChangeRepository transactions = mock(StateChangeRepository.class);
        StateContainerRepository accounts = mock(StateContainerRepository.class);
        StateChangeEntity transaction = new StateChangeEntity();
        transaction.setId(8L); transaction.setRawText("Paid 450");
        transaction.setRecordStatus(ExpenseRecordStatus.ACTIVE); transaction.setNeedsEnrichment(true);
        transaction.setRecordVersion(3);
        StateContainerEntity account = new StateContainerEntity();
        account.setId(4L); account.setName("HDFC card"); account.setContainerType("CREDIT_CARD");
        when(transactions.findNeedsEnrichment(eq("42"), any(Pageable.class))).thenReturn(List.of(transaction));
        when(accounts.findActiveByOwnerId(42L)).thenReturn(List.of(account));

        var queue = new DashboardEnrichmentService(
                transactions, accounts, mock(ExpenseCorrectionService.class),
                mock(ExpenseDraftService.class), mock(ExpenseHandler.class),
                mock(ExpenseSourceAccountResolver.class)).queue(42L);

        assertThat(queue.hasItems()).isTrue();
        assertThat(queue.items()).extracting(DashboardEnrichmentService.EnrichmentItem::type)
                .containsExactly("TRANSACTION", "ACCOUNT");
        assertThat(queue.items().getFirst().alertLabel()).startsWith("⚠");
        assertThat(queue.items().getFirst().version()).isEqualTo(3);
        assertThat(queue.items().getLast().version()).isNull();
        assertThat(queue.items().getLast().missingFields())
                .contains("currentBalance", "creditLimit", "billingDay", "dueDay");
    }

    @Test
    void discardUsesAuthenticatedUserAndExpectedVersion() {
        StateChangeRepository transactions = mock(StateChangeRepository.class);
        StateContainerRepository accounts = mock(StateContainerRepository.class);
        ExpenseCorrectionService corrections = mock(ExpenseCorrectionService.class);
        DashboardEnrichmentService service = new DashboardEnrichmentService(transactions, accounts, corrections,
                mock(ExpenseDraftService.class), mock(ExpenseHandler.class),
                mock(ExpenseSourceAccountResolver.class));

        service.discardTransaction(42L, 8L, 3);

        verify(corrections).voidEnrichmentExpense(42L, 8L, 3);
    }

    @Test
    void draftExposesResolvedAccountAlongsideDetectedPaymentRail() {
        StateChangeRepository transactions = mock(StateChangeRepository.class);
        StateContainerRepository accounts = mock(StateContainerRepository.class);
        ExpenseDraftService drafts = mock(ExpenseDraftService.class);
        ExpenseSourceAccountResolver resolver = mock(ExpenseSourceAccountResolver.class);
        ExpenseDraftEntity draft = new ExpenseDraftEntity();
        draft.setId(2L); draft.setUserId(42L); draft.setVersion(1); draft.setSourceChannel("WHATSAPP");
        draft.setRawText("booked room in oyo for 3654 paid using upi"); draft.setMissingFields(List.of());
        ExpenseDto dto = new ExpenseDto(); dto.setAmount(new BigDecimal("3654")); dto.setSourceAccount("UPI");
        dto.setCategory("Travel"); dto.setSubcategory("Accommodation");
        StateContainerEntity hdfc = new StateContainerEntity(); hdfc.setId(7L); hdfc.setName("hdfc bank account");
        when(drafts.pending(42L, 20)).thenReturn(List.of(draft));
        when(drafts.toDto(draft)).thenReturn(dto);
        when(resolver.resolve(dto, 42L)).thenReturn(hdfc);

        var queue = new DashboardEnrichmentService(transactions, accounts,
                mock(ExpenseCorrectionService.class), drafts, mock(ExpenseHandler.class), resolver).queue(42L);

        var item = queue.items().getFirst();
        assertThat(item.sourceAccount()).isEqualTo("UPI");
        assertThat(item.resolvedSourceAccountId()).isEqualTo(7L);
        assertThat(item.resolvedSourceAccountName()).isEqualTo("hdfc bank account");
    }
}
