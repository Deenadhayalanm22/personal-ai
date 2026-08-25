package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.state.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

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
        StateContainerEntity account = new StateContainerEntity();
        account.setId(4L); account.setName("HDFC card"); account.setContainerType("CREDIT_CARD");
        when(transactions.findNeedsEnrichment(eq("42"), any(Pageable.class))).thenReturn(List.of(transaction));
        when(accounts.findActiveByOwnerId(42L)).thenReturn(List.of(account));

        var queue = new DashboardEnrichmentService(transactions, accounts).queue(42L);

        assertThat(queue.hasItems()).isTrue();
        assertThat(queue.items()).extracting(DashboardEnrichmentService.EnrichmentItem::type)
                .containsExactly("TRANSACTION", "ACCOUNT");
        assertThat(queue.items().getFirst().alertLabel()).startsWith("⚠");
        assertThat(queue.items().getLast().missingFields())
                .contains("currentBalance", "creditLimit", "billingDay", "dueDay");
    }
}
